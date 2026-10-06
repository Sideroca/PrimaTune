#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
publish_preview.py —— 把预览图发到 GitHub 的「previews」发布里，并打印**加速直链**。

为什么：工作区文件浏览对人不友好（没有搜索），用户每次要翻很久。
       发上去后手机点一条链接就能看图（和 APK 直链同一套加速前缀）。

⚠️ GitHub 的 release 资产**只接受 ASCII 文件名**（中文名会 422 already_exists）——
   所以本地中文名一律映射成英文名，并在「发布说明」里给出对照表。

用法（本地路径=远端英文名，可多组）：
    python3 tools/publish_preview.py "预览/卡片顶距.jpg=card-gap.jpg" "预览/译文.jpg=trans-multi.jpg"
"""
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

REPO = "Sideroca/PrimaTune"
TAG = "previews"
MIRRORS = ["https://gh-proxy.com/", "https://ghfast.top/", "https://ghproxy.net/"]


def token():
    url = subprocess.check_output(["git", "remote", "get-url", "origin"], text=True).strip()
    return url.split("x-access-token:")[1].split("@")[0]


TOK = token()


def call(url, method="GET", data=None, raw=None, ctype="application/json"):
    h = {"Authorization": "token " + TOK, "Accept": "application/vnd.github+json",
         "User-Agent": "preview-bot"}
    body = raw
    if data is not None:
        body = json.dumps(data).encode()
    r = urllib.request.Request(url, method=method, data=body, headers=h)
    if raw is not None:
        r.add_header("Content-Type", ctype)
    try:
        with urllib.request.urlopen(r) as f:
            txt = f.read().decode(errors="replace")
            return f.status, (json.loads(txt) if txt.strip() else {})
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200]


def main(pairs):
    base = "https://api.github.com/repos/" + REPO
    st, rel = call(base + "/releases/tags/" + TAG)
    if st != 200:
        st, rel = call(base + "/releases", "POST",
                       {"tag_name": TAG, "name": "预览图（自动上传）", "body": "工程窗预览图"})
        print("已创建发布:", st)
    have = {a["name"]: a for a in rel.get("assets", [])}
    lines = []
    for pair in pairs:
        local, _, remote = pair.partition("=")
        f = pathlib.Path(local)
        remote = remote.strip() or f.stem
        if not f.exists():
            print("跳过（不存在）:", local)
            continue
        if remote in have:                      # 同名先删，避免堆版本
            call(base + "/releases/assets/" + str(have[remote]["id"]), "DELETE")
        up = "https://uploads.github.com/repos/%s/releases/%d/assets?name=%s" % (
            REPO, rel["id"], urllib.parse.quote(remote))
        st2, info = call(up, "POST", raw=f.read_bytes(), ctype="image/jpeg")
        print("上传", remote, "->", st2)
        lines.append("- `%s` —— %s" % (remote, f.stem))
        for m in MIRRORS:
            print("   ", m + "https://github.com/%s/releases/download/%s/%s" % (REPO, TAG, remote))
    if lines:
        body = "工程窗预览图（手机点链接直接看，加速前缀拼在原链前）：\n\n" + "\n".join(lines)
        call(base + "/releases/" + str(rel["id"]), "PATCH", {"body": body, "name": "预览图（自动上传）"})
        print("发布说明已更新（含对照表）")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("用法: python3 tools/publish_preview.py 路径[=英文名] ...")
    main(sys.argv[1:])
