/**
 * 认证相关工具方法
 */

interface JwtPayload {
  sub?: string;
  exp?: number;
  iat?: number;
}

/** 解析 JWT 的 payload，格式非法时返回 null */
function parseJwtPayload(token: string): JwtPayload | null {
  const segments = token.split('.');
  if (segments.length !== 3) {
    return null;
  }
  try {
    const base64 = segments[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const binary = atob(padded);
    // 还原 UTF-8 字符，避免 payload 中出现中文时乱码
    const json = decodeURIComponent(
      Array.from(binary)
        .map((char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

/**
 * 判断 token 是否可用：必须存在、是合法 JWT，且未过期。
 * 仅判断"字符串是否存在"会把已过期或伪造的 token 当成已登录状态。
 */
export function isTokenValid(token: string | null | undefined): boolean {
  if (!token) {
    return false;
  }
  const payload = parseJwtPayload(token);
  if (!payload || typeof payload.exp !== 'number') {
    return false;
  }
  return payload.exp * 1000 > Date.now();
}
