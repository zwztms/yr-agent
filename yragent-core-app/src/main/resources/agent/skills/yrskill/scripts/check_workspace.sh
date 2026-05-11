#!/bin/bash
# yrskill workspace 环境检查脚本
# 用于在执行前快速了解项目工作区状态

WORKSPACE="${1:-E:/xiangmu}"

echo "=== yragent 工作区检查 ==="
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "工作区: $WORKSPACE"
echo ""

echo "--- 工作区目录结构 ---"
if [ -d "$WORKSPACE" ]; then
    ls -la "$WORKSPACE" 2>/dev/null | head -20
else
    echo "工作区目录不存在: $WORKSPACE"
fi

echo ""
echo "--- 磁盘使用 ---"
df -h "$WORKSPACE" 2>/dev/null || echo "(磁盘信息不可用)"

echo ""
echo "--- Java 环境 ---"
java -version 2>&1 || echo "Java 未安装或不在 PATH 中"

echo ""
echo "--- Git 状态 ---"
if command -v git &> /dev/null; then
    if [ -d "$WORKSPACE/.git" ]; then
        cd "$WORKSPACE" && git status --short 2>/dev/null | head -10
    else
        echo "非 Git 仓库"
    fi
else
    echo "Git 未安装"
fi
