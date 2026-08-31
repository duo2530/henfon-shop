# 数据库迁移版本管理

`../init` 目录中的初始化脚本按三位数字前缀排序（`001`、`002`……），每个文件视为不可变迁移版本。版本清单 `manifest.sha256` 记录文件名及 SHA-256 摘要，用于在部署前发现脚本被覆盖、遗漏或乱序。

## 新增迁移

1. 在 `shop-admin/db/init` 新增下一个连续编号的 SQL 文件，例如 `023_add_xxx.sql`。
2. 执行校验脚本生成新的摘要（脚本检测到清单不一致时会提示更新清单）。
3. 人工审核 SQL 后，将新文件及 `manifest.sha256` 一并提交。已经发布的版本不得修改；修复请新增更高版本。

## 校验

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File shop-admin/db/migration/verify-migrations.ps1
```

校验内容包括：版本号连续、清单文件存在、磁盘文件未遗漏，以及每个文件的 SHA-256 与清单一致。命令以非零状态退出时禁止继续部署。
