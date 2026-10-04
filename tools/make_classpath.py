"""生成 javac 用的 classpath（Windows 风格）。

为什么需要它：这台机器没有 mvn，而把 ~/.m2 下所有 jar 一股脑塞进 -cp 会出事——
同一个 artifact 往往存在多个版本（例如 lombok 有 7 个版本），javac 会随机挂载最旧的那个，
lombok 1.18.16 在 JDK 21 上直接抛 IllegalAccessError，整个编译 exit 3 失败。
这里按「groupId + artifactId」分组，只保留版本号最高的一个。

用法：python tools/make_classpath.py > /tmp/cp_win.txt
"""
import os
import re
import sys
from pathlib import Path

REPO = Path.home() / ".m2" / "repository"


def ver_key(v: str):
    """把 1.18.16 / 3.5.5 / 2.0.0.Final 之类的版本号变成可比较的元组。"""
    nums = re.findall(r"\d+", v)
    return tuple(int(n) for n in nums) if nums else (0,)


def main() -> None:
    best: dict[str, tuple[tuple[int, ...], str]] = {}
    for root, _dirs, files in os.walk(REPO):
        for f in files:
            if not f.endswith(".jar"):
                continue
            if f.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar")):
                continue
            p = Path(root) / f
            try:
                version = p.parent.name          # .../<artifact>/<version>/x.jar
                artifact = p.parent.parent.name  # .../<artifact>/<version>/x.jar
                group = str(p.parent.parent.parent.relative_to(REPO))
            except (IndexError, ValueError):
                continue
            key = f"{group}:{artifact}"
            k = ver_key(version)
            if key not in best or k > best[key][0]:
                best[key] = (k, str(p))

    items = sorted(v[1] for v in best.values())
    win = []
    for p in items:
        q = p.replace("\\", "/")
        # /c/... -> C:/...，其余原样
        if re.match(r"^/[a-zA-Z]/", q):
            q = q[1].upper() + ":" + q[2:]
        win.append(q)
    sys.stdout.write(";".join(win))


if __name__ == "__main__":
    main()
