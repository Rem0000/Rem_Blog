#!/usr/bin/env bash
# 构建博客前台（必选）与后台管理端（可选），产物落到 ui/{blog,admin}/dist。
# Docker/手工部署都从这个目录取前端产物。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BLOG="$ROOT/blog-v3"
ADMIN="$ROOT/blog-v3-admin"
OUT="$ROOT/ui"
REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m[错误] %s\033[0m\n' "$*" >&2; exit 1; }

command -v node >/dev/null || die "未找到 node，请先安装 Node.js 18 或 20"
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
say "Node.js $(node -v) / npm $(npm -v)"
if [ "$NODE_MAJOR" -lt 18 ]; then
  die "Node.js 版本过低（$NODE_MAJOR），本项目需要 18 或 20"
fi
if [ "$NODE_MAJOR" -ge 22 ]; then
  echo "[提示] Node 22+ 未在项目 README 中声明，若构建异常请切到 18/20。"
fi

# ---------- 博客前台 ----------
say "安装前台依赖"
cd "$BLOG"
npm install --registry="$REGISTRY" --no-audit --no-fund

say "构建前台"
npm run build

[ -f "$BLOG/dist/index.html" ] || die "前台构建失败：没有生成 dist/index.html"
mkdir -p "$OUT/blog"
rm -rf "$OUT/blog/dist"
cp -r "$BLOG/dist" "$OUT/blog/dist"
say "前台产物 -> ui/blog/dist ($(find "$OUT/blog/dist" -type f | wc -l) 个文件)"

# ---------- 后台管理端（可选）----------
if [ "${SKIP_ADMIN:-0}" = "1" ]; then
  say "已跳过后台管理端（SKIP_ADMIN=1）"
  exit 0
fi

if ! command -v pnpm >/dev/null; then
  say "未找到 pnpm，尝试用 corepack 启用"
  if command -v corepack >/dev/null; then
    corepack enable && corepack prepare pnpm@7.27.0 --activate || true
  fi
fi
if ! command -v pnpm >/dev/null; then
  echo "[提示] 仍然没有 pnpm，跳过管理端构建。需要的话执行：npm i -g pnpm@7"
  exit 0
fi

say "安装管理端依赖（pnpm）"
cd "$ADMIN"
pnpm install --registry="$REGISTRY" || die "管理端依赖安装失败"

say "构建管理端"
pnpm build || die "管理端构建失败（可设 SKIP_ADMIN=1 先只部署前台）"

[ -d "$ADMIN/dist" ] || die "管理端构建失败：没有生成 dist"
mkdir -p "$OUT/admin"
rm -rf "$OUT/admin/dist"
cp -r "$ADMIN/dist" "$OUT/admin/dist"
say "管理端产物 -> ui/admin/dist ($(find "$OUT/admin/dist" -type f | wc -l) 个文件)"
