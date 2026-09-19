import { useCallback, useEffect, useState } from 'react';
import { buildPortalHash, HOME_ROUTE, parsePortalRoute } from '../utils/portalRoute';
import type { PortalRoute } from '../utils/portalRoute';

/**
 * 视图身份。同一身份内部的地址变化（如商品页切 `?tab=`）不该把滚动位置顶掉，
 * 换商品、换视图才滚到顶部。
 */
function routeIdentity(route: PortalRoute): string {
  return route.view === 'product' ? `product:${route.productId}` : route.view;
}

/**
 * 把整页视图绑定到 `location.hash`。
 *
 * `pushState`/`replaceState` 自己不触发任何事件，浏览器前进后退只会发 `popstate`（改 hash 时额外发
 * `hashchange`），所以视图必须由这两个事件回写，否则会出现「地址栏回去了、画面没动」。
 *
 * 进入新视图时滚到顶部；同一视图内部切换（页签、筛选这类只改查询串的变化）不滚，
 * 免得把用户刚看的位置顶掉。
 */
export function usePortalRoute() {
  const [route, setRoute] = useState<PortalRoute>(
    () => parsePortalRoute(window.location.hash) ?? HOME_ROUTE,
  );

  useEffect(() => {
    const syncFromLocation = () => {
      const next = parsePortalRoute(window.location.hash);
      // null 是正文锚点这类非路由 hash：保持当前视图，别把用户踢回首页。
      if (next) setRoute(next);
    };
    window.addEventListener('popstate', syncFromLocation);
    window.addEventListener('hashchange', syncFromLocation);
    return () => {
      window.removeEventListener('popstate', syncFromLocation);
      window.removeEventListener('hashchange', syncFromLocation);
    };
  }, []);

  /**
   * 跳转到一个整页视图。
   *
   * @param next 目标路由
   * @param options.replace 传 true 时不压新历史记录 —— 页内切换（页签、筛选）用它，
   *   否则每点一次页签就多一条历史，要按好几下后退才出得去
   */
  const navigate = useCallback((next: PortalRoute, options?: { replace?: boolean }) => {
    // 用 pathname + search 做基底：`pushState` 的第三个参数传空串会改成 currentURL，
    // 传 `#/orders` 这类会被解析成相对当前路径，拼全了才稳。
    const base = `${window.location.pathname}${window.location.search}`;
    const url = `${base}${buildPortalHash(next)}`;
    if (options?.replace) {
      window.history.replaceState({ portalRoute: next.view }, '', url);
    } else {
      window.history.pushState({ portalRoute: next.view }, '', url);
    }
    if (routeIdentity(route) !== routeIdentity(next)) window.scrollTo({ top: 0 });
    setRoute(next);
  }, [route]);

  return { route, navigate };
}
