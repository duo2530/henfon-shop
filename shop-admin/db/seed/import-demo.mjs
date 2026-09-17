// 把 db/seed 下的演示媒体与业务数据导入本地环境，让新克隆的仓库开箱即有图有数据。
//
// 用法（在 shop-admin 目录执行）：
//   node db/seed/import-demo.mjs           # 上传媒体到 MinIO，并提示 SQL 导入命令
//   node db/seed/import-demo.mjs --force   # 覆盖 MinIO 上已存在的对象
//   node db/seed/import-demo.mjs --sql     # 顺带用 mysql 客户端导入 demo-data.sql
//
// 环境变量（默认值与 application-dev.yml 一致）：
//   MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / MINIO_BUCKET
//   导入 SQL 时额外需要 MYSQL_BIN（mysql 客户端绝对路径）、MYSQL_HOST、MYSQL_PORT、MYSQL_USER、MYSQL_PWD、MYSQL_DB
//
// 依赖：零依赖，原生 fetch + 手写 SigV4，需要 Node 18 以上。
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ENDPOINT = (process.env.MINIO_ENDPOINT || 'http://127.0.0.1:9000').replace(/\/$/, '');
const BUCKET = process.env.MINIO_BUCKET || 'shop';
const ACCESS_KEY = process.env.MINIO_ACCESS_KEY || 'minioadmin';
const SECRET_KEY = process.env.MINIO_SECRET_KEY || 'minioadmin';
const REGION = 'us-east-1';

const FORCE = process.argv.includes('--force');
const WITH_SQL = process.argv.includes('--sql');

const SEED_DIR = path.dirname(fileURLToPath(import.meta.url));
const MEDIA_DIR = path.join(SEED_DIR, 'media');
const SQL_FILE = path.join(SEED_DIR, 'demo-data.sql');

/** 对象键前缀：媒体文件在仓库里按「去掉 media/ 前缀」存放，上传时补回去。 */
const KEY_PREFIX = 'media/';

const CONTENT_TYPES = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.mp4': 'video/mp4',
  '.pdf': 'application/pdf',
};

if (typeof fetch !== 'function') {
  console.error('需要 Node 18 及以上版本（原生 fetch）。');
  process.exit(1);
}

const sha256Hex = (value) => crypto.createHash('sha256').update(value).digest('hex');
const hmac = (key, value) => crypto.createHmac('sha256', key).update(value).digest();
const signingKey = (dateStamp) =>
  hmac(hmac(hmac(hmac(`AWS4${SECRET_KEY}`, dateStamp), REGION), 's3'), 'aws4_request');

/**
 * 生成 SigV4 签名头。
 *
 * @param {string} method HTTP 方法
 * @param {string} pathname 以 / 开头的路径
 * @param {object} query 查询参数
 * @param {Buffer|null} body 请求体，GET/HEAD 传 null
 * @param {object} extraHeaders 需要参与签名的额外头
 */
function signedHeaders(method, pathname, query = {}, body = null, extraHeaders = {}) {
  const amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
  const dateStamp = amzDate.slice(0, 8);
  const payloadHash = sha256Hex(body ?? '');
  const host = new URL(ENDPOINT).host;
  const headers = {
    host,
    'x-amz-content-sha256': payloadHash,
    'x-amz-date': amzDate,
    ...Object.fromEntries(Object.entries(extraHeaders).map(([k, v]) => [k.toLowerCase(), v])),
  };
  const names = Object.keys(headers).sort();
  const canonicalHeaders = names.map((n) => `${n}:${String(headers[n]).trim()}\n`).join('');

  const canonicalQuery = Object.entries(query)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
  const canonicalRequest = [
    method,
    pathname,
    canonicalQuery,
    canonicalHeaders,
    names.join(';'),
    payloadHash,
  ].join('\n');

  const scope = `${dateStamp}/${REGION}/s3/aws4_request`;
  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, scope, sha256Hex(canonicalRequest)].join('\n');
  const signature = crypto.createHmac('sha256', signingKey(dateStamp)).update(stringToSign).digest('hex');

  return {
    'x-amz-date': amzDate,
    'x-amz-content-sha256': payloadHash,
    ...extraHeaders,
    Authorization: `AWS4-HMAC-SHA256 Credential=${ACCESS_KEY}/${scope}, `
      + `SignedHeaders=${names.join(';')}, Signature=${signature}`,
  };
}

const unescapeXml = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
  .replace(/&quot;/g, '"').replace(/&#39;/g, "'");

/** 桶不存在时先建，避免首次部署导入失败。 */
async function ensureBucket() {
  // HEAD 也要带签名，否则 MinIO 一律回 403，会把已存在的桶误判成需要创建。
  const head = await fetch(`${ENDPOINT}/${BUCKET}`, {
    method: 'HEAD',
    headers: signedHeaders('HEAD', `/${BUCKET}`),
  });
  if (head.status === 200) return;
  if (head.status !== 404) {
    console.log(`检查桶 ${BUCKET} 返回 ${head.status}，继续尝试创建`);
  }
  const created = await fetch(`${ENDPOINT}/${BUCKET}`, {
    method: 'PUT',
    headers: signedHeaders('PUT', `/${BUCKET}`),
  });
  if (created.status === 409) {
    console.log(`桶 ${BUCKET} 已存在，跳过创建`);
    return;
  }
  if (!created.ok) {
    throw new Error(`创建桶 ${BUCKET} 失败：${created.status} ${await created.text()}`);
  }
  console.log(`已创建桶 ${BUCKET}`);
}

/** 列出桶内已有对象键，用于增量跳过。 */
async function listExistingKeys() {
  const keys = new Set();
  let token = null;
  do {
    const query = { 'list-type': '2', 'max-keys': '1000' };
    if (token) query['continuation-token'] = token;
    const url = new URL(`${ENDPOINT}/${BUCKET}`);
    for (const [k, v] of Object.entries(query)) url.searchParams.set(k, v);
    const res = await fetch(url, { headers: signedHeaders('GET', `/${BUCKET}`, query) });
    if (!res.ok) throw new Error(`列举对象失败：${res.status} ${await res.text()}`);
    const xml = await res.text();
    for (const m of xml.matchAll(/<Contents>([\s\S]*?)<\/Contents>/g)) {
      const key = unescapeXml((m[1].match(/<Key>([\s\S]*?)<\/Key>/) || [])[1] ?? '');
      if (key) keys.add(key);
    }
    token = /<IsTruncated>true<\/IsTruncated>/.test(xml)
      ? unescapeXml((xml.match(/<NextContinuationToken>([\s\S]*?)<\/NextContinuationToken>/) || [])[1] ?? '')
      : null;
  } while (token);
  return keys;
}

async function uploadMedia() {
  if (!fs.existsSync(MEDIA_DIR)) {
    throw new Error(`找不到媒体目录 ${MEDIA_DIR}`);
  }
  await ensureBucket();
  const existing = FORCE ? new Set() : await listExistingKeys();

  const files = [];
  (function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else files.push(full);
    }
  })(MEDIA_DIR);

  console.log(`待上传 ${files.length} 个文件，已有对象 ${existing.size} 个${FORCE ? '（--force 覆盖模式）' : ''}`);

  let uploaded = 0;
  let skipped = 0;
  let bytes = 0;
  const failures = [];
  for (const file of files) {
    const relative = path.relative(MEDIA_DIR, file).split(path.sep).join('/');
    const key = KEY_PREFIX + relative;
    if (existing.has(key)) {
      skipped += 1;
      continue;
    }
    // 对象键里的目录分隔符要逐段编码，直接 encodeURIComponent 会把 / 也转义掉。
    const encodedKey = key.split('/').map(encodeURIComponent).join('/');
    const body = fs.readFileSync(file);
    const contentType = CONTENT_TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream';
    try {
      const res = await fetch(`${ENDPOINT}/${BUCKET}/${encodedKey}`, {
        method: 'PUT',
        headers: signedHeaders('PUT', `/${BUCKET}/${encodedKey}`, {}, body, { 'content-type': contentType }),
        body,
      });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      uploaded += 1;
      bytes += body.length;
      if (uploaded % 50 === 0) console.log(`  ... ${uploaded}/${files.length}`);
    } catch (error) {
      failures.push(`${key}: ${error.message}`);
    }
  }

  console.log(`媒体上传完成：新增 ${uploaded} 个，跳过 ${skipped} 个，共 ${(bytes / 1048576).toFixed(2)} MB`);
  if (failures.length) {
    console.log(`失败 ${failures.length} 个：`);
    for (const f of failures) console.log(`  ${f}`);
    process.exitCode = 1;
  }
}

function importSql() {
  if (!fs.existsSync(SQL_FILE)) {
    throw new Error(`找不到 ${SQL_FILE}`);
  }
  const bin = process.env.MYSQL_BIN || 'mysql';
  const args = [
    '-h', process.env.MYSQL_HOST || '127.0.0.1',
    '-P', process.env.MYSQL_PORT || '3306',
    '-u', process.env.MYSQL_USER || 'root',
    '--default-character-set=utf8mb4',
    process.env.MYSQL_DB || 'henfon-shop',
  ];
  console.log(`导入 ${path.basename(SQL_FILE)} → ${args[args.length - 1]}`);
  const result = spawnSync(bin, args, {
    input: fs.readFileSync(SQL_FILE),
    stdio: ['pipe', 'inherit', 'inherit'],
  });
  if (result.error) {
    throw new Error(`调用 ${bin} 失败（可用 MYSQL_BIN 指定绝对路径，密码走 MYSQL_PWD 环境变量）：${result.error.message}`);
  }
  if (result.status !== 0) {
    throw new Error(`导入退出码 ${result.status}`);
  }
  console.log('业务数据导入完成');
}

await uploadMedia();
if (WITH_SQL) {
  importSql();
} else {
  console.log('\n业务数据未导入。执行下面任一方式即可：');
  console.log('  node db/seed/import-demo.mjs --sql        （需 mysql 客户端在 PATH 或用 MYSQL_BIN 指定）');
  console.log('  mysql -uroot -p --default-character-set=utf8mb4 henfon-shop < db/seed/demo-data.sql');
}
