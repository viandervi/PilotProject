import type { NextConfig } from "next";
const backend = process.env.CREATORRX_BACKEND_URL || "http://localhost:8080";
const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/auth/:path*", destination: `${backend}/auth/:path*` },
      { source: "/oauth/:path*", destination: `${backend}/oauth/:path*` },
    ];
  },
};
export default nextConfig;
