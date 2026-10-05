#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
preflight_check.py —— 推送前的"零成本静态体检"（不跑 Gradle、不占内存）

查两类**我们真崩过**的问题（Android Lint 不管这些）：

  ① **启动顺序**：`lateinit var` 字段在 `findViewById` 赋值**之前**就被用到
     （按 `onCreate` 的语句顺序 + 同文件函数调用链模拟执行；`::x.isInitialized` 视为已防呆）
  ② **资源 id 校验**：代码里 `R.id.foo` 引用的 id，在 `res/**/*.xml` 里**不存在**（typo / 删了没清）

用法：
    python3 tools/preflight_check.py            # 在仓库根跑
退出码：0 = 没发现问题；1 = 有问题（CI 里据此判红）
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
KT_DIRS = [ROOT / "app" / "src" / "main" / "java"]
RES_DIRS = [ROOT / "app" / "src" / "main" / "res"]

DECL = re.compile(r'^(\s*)(?:private\s+|internal\s+|public\s+)?lateinit\s+var\s+(\w+)\s*:')
FUN = re.compile(r'^(\s*)(?:override\s+|private\s+|internal\s+|public\s+|suspend\s+)*fun\s+(?:<[^>]*>\s*)?(\w+)\s*\(')
ASSIGN_ANY = re.compile(r'^(\s*)(\w+)\s*=\s*(?!=)')      # 任意赋值（不只 findViewById）
DEFERRED = ("setOnClickListener", "setOnItemClickListener", "setOnTouchListener",
            "setOnFocusChangeListener", "setOnEditorActionListener", "addTextChangedListener",
            "setOnSeekBarChangeListener", "addUpdateListener", "setOnDismissListener",
            "setOnShowListener", "postDelayed")
CALL = re.compile(r'\b(\w+)\s*\(')


def kt_files():
    for d in KT_DIRS:
        if d.exists():
            yield from d.rglob("*.kt")


def load(p):
    return p.read_text(encoding="utf-8", errors="replace").splitlines()


def check_file(path):
    lines = load(path)
    fields, funcs = set(), {}          # funcs: name -> (起始行, 缩进)
    for i, t in enumerate(lines, 1):
        m = DECL.match(t)
        if m:
            fields.add(m.group(2))
        m = FUN.match(t)
        if m:
            funcs[m.group(2)] = (i, len(m.group(1)))
    if not fields or "onCreate" not in funcs:
        return []
    # 函数范围 = 从起始行 到**下一个缩进不超过它的** fun 之前（避免把嵌套/后面的函数吞进来）
    rng = {}
    for name, (s, ind) in funcs.items():
        nxt = len(lines) + 1
        for other, (s2, ind2) in funcs.items():
            if other != name and s2 > s and ind2 <= ind:
                nxt = min(nxt, s2)
        rng[name] = (s, nxt - 1)

    warned = set()
    out = []
    pending = []          # 延迟（回调/动画）里的调用：**整趟走查完成后**再处理

    # 进了这些"稍后才跑"的块（监听器/动画），里面的代码不能当立即执行
    DEFERRED_OPEN = ("setOnClickListener", "setOnItemClickListener", "setOnTouchListener",
                     "setOnFocusChangeListener", "setOnEditorActionListener", "addTextChangedListener",
                     "setOnSeekBarChangeListener", "addUpdateListener", "setOnDismissListener",
                     "setOnShowListener", "postDelayed", "Ratchet.attach")

    def walk(fn, already, stack, guarded):
        if fn in stack or fn not in rng:                      # 防递归
            return
        s, e = rng[fn]
        body = "\n".join(lines[s - 1:e])
        g = set(re.findall(r'::(\w+)\.isInitialized', body)) | guarded
        deferred_depth = 0
        for ln in range(s + 1, e + 1):        # 跳过函数签名行（它的 "{" 是本函数体，不是回调）
            text = lines[ln - 1]
            opens, closes = text.count("{"), text.count("}")
            inside_deferred = deferred_depth > 0 or any(k in text for k in DEFERRED_OPEN)
            m = ASSIGN_ANY.match(text)
            if m and m.group(2) in fields:
                already.add(m.group(2))
                deferred_depth = max(0, deferred_depth + opens - closes)
                continue
            if "lateinit" not in text:
                for callee in CALL.findall(text):
                    if callee in rng and callee != fn:
                        if inside_deferred:
                            pending.append(callee)            # 稍后才跑 → 最后统一看
                        else:
                            walk(callee, already, stack + [fn], g)
                if not inside_deferred:
                    for f in fields:
                        if f in already or f in g:
                            continue
                        if re.search(r'\b' + re.escape(f) + r'\b', text):
                            key = (f, fn)
                            if key not in warned:
                                warned.add(key)
                                out.append(
                                    "  %s:%d  字段 `%s` 在初始化前被 `%s()` 用到 → 会崩（lateinit 未初始化）"
                                    % (path.name, ln, f, fn)
                                )
            deferred_depth = max(0, deferred_depth + opens - closes)

    all_assigned = set()
    walk("onCreate", all_assigned, [], set())
    # 回调体：此时"同步路径"的赋值都已完成，再看它们（这才是真机点按钮时的状态）
    for callee in set(pending):
        walk(callee, all_assigned, [], set())
    return out


def check_ids():
    """代码里的 R.id.xxx 必须在 res 里有 @+id/xxx"""
    defined = set()
    for d in RES_DIRS:
        for p in d.rglob("*.xml"):
            defined |= set(re.findall(r'@\+id/(\w+)', p.read_text(encoding="utf-8", errors="replace")))
    bad = []
    for p in kt_files():
        for i, t in enumerate(load(p), 1):
            for _ in re.finditer(r'(?<!android\.)\bR\.id\.(\w+)', t):
                pass
            for m in re.finditer(r'(?<!android\.)\bR\.id\.(\w+)', t):
                if m.group(1) not in defined:
                    bad.append("  %s:%d  `R.id.%s` 在 res 里不存在（typo / 删了没清）"
                               % (p.name, i, m.group(1)))
    return bad


def main():
    problems = []
    for p in kt_files():
        problems += check_file(p)
    problems += check_ids()
    print("=== preflight_check ===")
    if not problems:
        print("✅ 未发现：启动顺序问题 / 不存在的资源 id")
        return 0
    print("❌ 发现 %d 处：" % len(problems))
    for x in problems:
        print(x)
    return 1


if __name__ == "__main__":
    sys.exit(main())
