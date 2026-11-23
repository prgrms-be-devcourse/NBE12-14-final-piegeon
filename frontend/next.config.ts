import type { NextConfig } from "next";

// 브라우저는 항상 같은 주소(/api/...)로 부르고, Next 서버가 백엔드로 넘긴다.
// 그래서 세션 쿠키가 프론트 주소에 붙고 CORS 설정이 필요 없다. 사진 API의 302와 SSE도 그대로 지나간다.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // 압축하면 SSE가 모였다가 한 번에 나가서 실시간이 늦어진다
  compress: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
