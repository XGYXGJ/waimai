#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查 .vue 文件模板里引用了、但 <script setup> 里没有定义的标识符。

背景：这类错误（如 Chat.vue 里写 {{ me }} 而 me 从未定义）构建期查不出来 ——
vite build 只做转译不做作用域分析，只有真正渲染那一行才会抛
"Property X was accessed during render but is not defined on instance"。
本脚本用启发式在提交前把它们挑出来。

用法：python check_undefined_refs.py [目录...]
默认扫 waimai-web/apps 与 packages 下的全部 .vue。
"""
import os
import re
import sys

# Vue / JS 全局与模板内置，出现在模板里是合法的
GLOBALS = {
    # Vue 模板内置（编译器注入）
    '$emit', '$props', '$slots', '$refs', '$el', '$attrs', '$data', '$options',
    '$route', '$router', '$nextTick', '$forceUpdate', '$watch',
    # JS 内置
    'true', 'false', 'null', 'undefined', 'this', 'typeof', 'instanceof', 'in', 'of',
    'new', 'void', 'delete', 'await', 'async', 'return', 'if', 'else', 'for', 'while',
    'do', 'switch', 'case', 'break', 'continue', 'default', 'try', 'catch', 'finally',
    'throw', 'function', 'class', 'extends', 'super', 'import', 'export', 'from',
    'NaN', 'Infinity', 'Math', 'JSON', 'Object', 'Array', 'String', 'Number', 'Boolean',
    'Date', 'RegExp', 'Error', 'Promise', 'Map', 'Set', 'Symbol', 'parseInt', 'parseFloat',
    'isNaN', 'isFinite', 'encodeURIComponent', 'decodeURIComponent', 'setTimeout',
    'setInterval', 'clearTimeout', 'clearInterval', 'console', 'window', 'document',
    'navigator', 'localStorage', 'sessionStorage', 'location', 'history', 'fetch',
    'undefined',
}

# 表达式里常见的「关键字式」片段，扫描时整体剔除
STRIP_PATTERNS = [
    re.compile(r'//[^\n]*'),
    re.compile(r'/\*.*?\*/', re.S),
    re.compile(r"'(?:[^'\\]|\\.)*'", re.S),
    re.compile(r'"(?:[^"\\]|\\.)*"', re.S),
    re.compile(r'`(?:[^`\\]|\\.)*`', re.S),
    re.compile(r'\$slots[\s\S]*?\}\}'),
    re.compile(r'/[^/\s][^\n]*/[gimsuy]*'),   # 正则字面量
]

# TypeScript 内置类型标注，不是变量
TS_TYPES = {
    'boolean', 'string', 'number', 'any', 'unknown', 'never', 'void', 'object',
    'symbol', 'bigint', 'null', 'undefined', 'Record', 'Array', 'Partial', 'Required',
    'Readonly', 'Pick', 'Omit', 'Map', 'Set', 'Promise',
}

# 内联箭头函数的参数：需先加进作用域，否则 (v: any) => 里的 v 会被误报
ARROW_PARAMS_RE = re.compile(r'\(\s*([^()]*?)\s*\)\s*(?::[^=]*?)?=>')

# 声明语句：抓出脚本里真正定义过的名字
DECL_RE = re.compile(
    r'\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)'
    r'|\bfunction\s*\*?\s*([A-Za-z_$][\w$]*)'
    r'|\bclass\s+([A-Za-z_$][\w$]*)'
    r'|\bimport\s+([A-Za-z_$][\w$]*)\b'
    r'|\bimport\s*\{([^}]*)\}'
    r'|\bimport\s+([A-Za-z_$][\w$]*)\s+from'
)
# 解构声明：const { a, b } = ... / const [a, b] = ...
DESTRUCT_RE = re.compile(r'\b(?:const|let|var)\s*[\{\[]([^\}\]]+)[\}\]]')
# v-for 引入的循环变量
VFOR_RE = re.compile(r'v-for\s*=\s*["\']\s*\(([^)]*)\)\s*(?:in|of)\s')
VFOR_SINGLE_RE = re.compile(r'v-for\s*=\s*["\']\s*([A-Za-z_$][\w$]*)\s+(?:in|of)\s')
# 插槽作用域（element-plus / vant）
SLOT_PROP_RE = re.compile(r'#\w+="\{([^}]*)\}"')
SLOT_PROP_SINGLE_RE = re.compile(r'#\w+="([A-Za-z_$][\w$]*)"')
# 模板表达式来源
EXPR_RE = re.compile(
    r'(?:\{\{(.*?)\}\})'          # 插值
    r'|(?::[\w.\-]+="(.*?)")'      # 绑定
    r'|(?:v-(?:if|else-if|show|model|for|html|text)=["\'](.*?)["\'])'
    r'|(?:@[\w.\-]+="(.*?)")',     # 事件
    re.S,
)
IDENT_RE = re.compile(r'[A-Za-z_$][\w$]*')


def scan_identifiers(expr: str):
    """
    逐字符扫描表达式里的标识符，跳过「对象字面量的键」与「属性访问」。

    误报的两个主要来源：
      { color: c.color }   -> color 是键，不是变量
      { zIndex: 100 }      -> 同上
    做法：跟踪花括号深度，深度 > 0 时若 token 紧跟 ':'，判为键并跳过。
    三元运算符 a ? b : c 里的 b/c 不在花括号内，不会被误跳。
    """
    out = []
    depth = 0
    i = 0
    n = len(expr)
    while i < n:
        ch = expr[i]
        # 只跟踪花括号深度：只有 {} 才是对象字面量。
        # 圆括号/方括号不计入，否则 (a ? me : b) 里的 me 会被误判成对象键而漏报。
        if ch == '{':
            depth += 1
            i += 1
            continue
        if ch == '}':
            depth -= 1
            i += 1
            continue
        if ch.isalpha() or ch in '_$':
            j = i
            while j < n and (expr[j].isalnum() or expr[j] in '_$'):
                j += 1
            word = expr[i:j]
            prev = expr[i - 1] if i > 0 else ''
            k = j
            while k < n and expr[k] == ' ':
                k += 1
            followed_by_colon = k < n and expr[k] == ':'
            is_prop = prev == '.'          # form.apiKey / c.color / $route.path
            is_key = depth > 0 and followed_by_colon and prev not in '.?'
            if not is_prop and not is_key:
                out.append(word)
            i = j
            continue
        i += 1
    return out


def strip_noise(expr: str) -> str:
    for p in STRIP_PATTERNS:
        expr = p.sub(' ', expr)
    return expr


def arrow_params(expr: str):
    """收集内联箭头函数的参数名（含 TS 类型标注的左值部分）"""
    names = set()
    for m in ARROW_PARAMS_RE.finditer(expr):
        for part in re.split(r'[,\s]+', m.group(1)):
            part = part.strip()
            if not part:
                continue
            name = part.split(':')[0].split('=')[0].strip()
            if name and re.match(r'^[A-Za-z_$][\w$]*$', name):
                names.add(name)
    return names


def declared_names(script: str) -> set:
    names = set()
    for m in DECL_RE.finditer(script):
        for g in m.groups():
            if not g:
                continue
            if ',' in g or '{' in g:      # import { a, b }
                for part in re.split(r'[,\s]+', g):
                    part = part.strip()
                    if part:
                        names.add(part.split(' as ')[-1].strip())
            else:
                names.add(g.strip())
    for m in DESTRUCT_RE.finditer(script):
        for part in re.split(r'[,\s]+', m.group(1)):
            part = part.strip().split(':')[-1].strip()
            if part and re.match(r'^[A-Za-z_$][\w$]*$', part):
                names.add(part)
    # 组件名（import X from / import Vant 里的全局注册组件）
    names |= {'VanButton', 'VanTag', 'VanCell', 'VanCellGroup', 'VanIcon', 'VanEmpty',
              'VanNavBar', 'VanTabbar', 'VanTabbarItem', 'VanList', 'VanPullRefresh',
              'VanField', 'VanButton', 'VanImage', 'VanUploader', 'VanNoticeBar',
              'VanPopup', 'VanActionSheet', 'VanSwitch', 'VanTabs', 'VanTab',
              'VanLoading', 'VanDialog', 'ElTable', 'ElTableColumn', 'ElButton',
              'ElCard', 'ElEmpty', 'ElInput', 'ElUpload', 'ElTag', 'ElMenu', 'ElMenuItem',
              'ElSubMenu', 'ElForm', 'ElFormItem', 'ElSelect', 'ElOption', 'ElDatePicker',
              'ElDialog', 'ElBadge', 'ElAlert', 'ElStatistic', 'ElDescriptions',
              'ElDescriptionsItem', 'ElProgress', 'ElTooltip', 'ElPopconfirm', 'ElDropdown',
              'router', 'route', 'RouterLink', 'RouterView'}
    return names


def check_file(path: str):
    try:
        src = open(path, encoding='utf-8').read()
    except Exception:
        return []
    m = re.search(r'<script\b[^>]*>(.*?)</script>', src, re.S)
    script = m.group(1) if m else ''
    tpl_m = re.search(r'<template\b[^>]*>(.*)</template>', src, re.S)
    if not tpl_m:
        return []
    tpl = tpl_m.group(1)

    declared = declared_names(script)
    # 模板作用域注入
    scope = set(declared) | set(GLOBALS)

    # v-for / 插槽作用域变量
    for mm in VFOR_RE.finditer(tpl):
        for part in re.split(r'[,\s]+', mm.group(1)):
            part = part.strip()
            if part:
                scope.add(part)
    for mm in VFOR_SINGLE_RE.finditer(tpl):
        scope.add(mm.group(1))
    for mm in SLOT_PROP_RE.finditer(tpl):
        for part in re.split(r'[,\s]+', mm.group(1)):
            part = part.strip()
            if part:
                scope.add(part)
    for mm in SLOT_PROP_SINGLE_RE.finditer(tpl):
        scope.add(mm.group(1))

    problems = []
    for em in EXPR_RE.finditer(tpl):
        raw = next((g for g in em.groups() if g), '')
        expr = strip_noise(raw)
        if not expr.strip():
            continue
        # 内联箭头函数的参数在本表达式内有效
        local = arrow_params(expr)
        for ident in set(scan_identifiers(expr)):
            if ident in scope or ident in local or ident in TS_TYPES:
                continue
            problems.append((ident, raw.strip().replace('\n', ' ')[:90]))
    return sorted(set(problems))


def main():
    # Windows 控制台默认 GBK，输出中文 / ¥ 等字符会抛 UnicodeEncodeError
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass
    roots = sys.argv[1:] or ['apps', 'packages']
    files = []
    for root in roots:
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames
                           if d not in ('node_modules', 'dist', '.git', 'logs')]
            for fn in filenames:
                if fn.endswith('.vue'):
                    files.append(os.path.join(dirpath, fn))
    total = 0
    for f in sorted(files):
        probs = check_file(f)
        if probs:
            print(f'\n!! {f}')
            for ident, raw in probs:
                print(f'     未定义: {ident:24}  用在: {raw}')
            total += len(probs)
    print(f'\n扫描 {len(files)} 个 .vue，发现 {total} 处可疑引用')
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main())
