#!/usr/bin/env python3
"""Merge SmartTube beta/stable sources and their media modules into RelayTube."""

from __future__ import annotations

import base64
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELAYTUBE_REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "saappleg/RelayTube")
SMARTTUBE_REPOSITORY = "yuliskov/SmartTube"
UPSTREAM_ROOT_URL = "https://github.com/yuliskov/SmartTube.git"
MODULES = {
    "MediaServiceCore": {
        "repository": "MediaServiceCore",
        "upstream": "https://github.com/yuliskov/MediaServiceCore.git",
        "branches": {
            "beta": "codex/relaytube-mediaservicecore-beta",
            "stable": "codex/relaytube-mediaservicecore-stable",
        },
    },
    "SharedModules": {
        "repository": "SharedModules",
        "upstream": "https://github.com/yuliskov/SharedModules.git",
        "branches": {
            "beta": "codex/relaytube-smarttube-sync",
            "stable": "codex/relaytube-sharedmodules-stable",
        },
    },
}
CHANNELS = {
    "beta": "codex/relaytube-release-next",
    "stable": "codex/relaytube-stable",
}
SYNC_TOKEN = os.environ.get("UPSTREAM_SYNC_TOKEN", "")


def run(args: list[str], cwd: Path = ROOT, *, check: bool = True) -> str:
    process = subprocess.run(args, cwd=cwd, text=True, capture_output=True)
    if check and process.returncode:
        details = (process.stderr or process.stdout).strip()
        raise RuntimeError(f"{args[0]} command failed ({process.returncode}): {details}")
    return process.stdout.strip()


def git(args: list[str], cwd: Path = ROOT, *, check: bool = True) -> str:
    return run(["git", *args], cwd, check=check)


def is_ancestor(older: str, newer: str, cwd: Path) -> bool:
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", older, newer],
        cwd=cwd,
        text=True,
        capture_output=True,
    )
    if result.returncode not in (0, 1):
        raise RuntimeError(f"Could not compare Git ancestry in {cwd}: {result.stderr.strip()}")
    return result.returncode == 0


def push_module(path: Path, branch: str) -> None:
    authorization = base64.b64encode(f"x-access-token:{SYNC_TOKEN}".encode()).decode()
    extra_header = f"AUTHORIZATION: basic {authorization}"
    result = subprocess.run(
        [
            "git",
            "-c",
            "http.https://github.com/.extraheader=",
            "-c",
            f"http.https://github.com/.extraheader={extra_header}",
            "push",
            "origin",
            f"HEAD:refs/heads/{branch}",
        ],
        cwd=path,
        text=True,
        capture_output=True,
    )
    if result.returncode:
        # Never include the extra-header argument in an error message.
        details = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"Could not push {path.name} to saappleg/{path.name}:{branch}: {details}")
    print(result.stdout.strip())


def sync_module(path: Path, name: str, channel: str, source_ref: str) -> str:
    info = MODULES[name]
    branch = info["branches"][channel]
    fork_url = f"https://github.com/saappleg/{info['repository']}.git"

    remote_url = git(["remote", "get-url", "origin"], path, check=False)
    if remote_url:
        git(["remote", "set-url", "origin", fork_url], path)
    else:
        git(["remote", "add", "origin", fork_url], path)

    git(["fetch", "origin", f"refs/heads/{branch}:refs/remotes/origin/{branch}"], path)
    git(["checkout", "--detach", f"refs/remotes/origin/{branch}"], path)
    git(["submodule", "sync", "--recursive"], path)
    git(["submodule", "update", "--init", "--recursive"], path)
    git(["fetch", "--no-tags", info["upstream"], source_ref], path)
    target = git(["rev-parse", "FETCH_HEAD"], path)
    head = git(["rev-parse", "HEAD"], path)

    if is_ancestor(target, head, path):
        return head

    if is_ancestor(head, target, path):
        git(["checkout", "--detach", target], path)
    else:
        result = subprocess.run(
            ["git", "merge", "--no-ff", "--no-commit", target],
            cwd=path,
            text=True,
            capture_output=True,
        )
        conflicts = git(["diff", "--name-only", "--diff-filter=U"], path).splitlines()
        if result.returncode and not conflicts:
            details = (result.stderr or result.stdout).strip()
            raise RuntimeError(f"Could not merge {name} upstream {target}: {details}")

        unexpected = [item for item in conflicts if item != "SharedModules"]
        if unexpected:
            raise RuntimeError(f"Unresolved {name} conflicts: {', '.join(unexpected)}")

        if "SharedModules" in conflicts:
            nested = git(["ls-tree", target, "SharedModules"], path).split()
            if len(nested) < 3 or nested[1] != "commit":
                raise RuntimeError(f"Could not read {name} upstream SharedModules pin")
            nested_path = path / "SharedModules"
            git(
                [
                    "fetch",
                    "https://github.com/yuliskov/SharedModules.git",
                    nested[2],
                ],
                nested_path,
            )
            git(["checkout", "--detach", nested[2]], nested_path)
            git(["add", "SharedModules"], path)

        git(["submodule", "sync", "--recursive"], path)
        git(["submodule", "update", "--init", "--recursive"], path)
        git(["commit", "-m", f"Merge SmartTube {name} {channel} updates"], path)

    git(["submodule", "sync", "--recursive"], path)
    git(["submodule", "update", "--init", "--recursive"], path)
    push_module(path, branch)
    return git(["rev-parse", "HEAD"], path)


def preserve_relaytube_version_override() -> None:
    build_file = ROOT / "smarttubetv/build.gradle"
    with build_file.open("r", encoding="utf-8", newline="") as source:
        contents = source.read()
    newline = "\r\n" if "\r\n" in contents else "\n"
    conflict = re.compile(
        r"(?m)^<<<<<<< [^\r\n]*\r?\n.*?^=======[ \t]*\r?\n.*?^>>>>>>> [^\r\n]*\r?\n",
        re.DOTALL,
    )
    contents, replacements = conflict.subn(
        f"        versionCode relayTubeVersionCode{newline}"
        f"        versionName relayTubeVersionName{newline}",
        contents,
    )
    if replacements != 1:
        raise RuntimeError("Unexpected smarttubetv/build.gradle conflict; refusing automatic resolution")
    with build_file.open("w", encoding="utf-8", newline="") as destination:
        destination.write(contents)
    git(["add", "smarttubetv/build.gradle"])


def set_fork_module_urls() -> None:
    git(
        [
            "config",
            "-f",
            ".gitmodules",
            "submodule.SharedModules.url",
            "https://github.com/saappleg/SharedModules",
        ]
    )
    git(
        [
            "config",
            "-f",
            ".gitmodules",
            "submodule.MediaServiceCore.url",
            "https://github.com/saappleg/MediaServiceCore",
        ]
    )
    git(["add", ".gitmodules"])


def sync_app_channel(channel: str, source_commit: str, module_commits: dict[str, str]) -> tuple[bool, str]:
    branch = CHANNELS[channel]
    git(["fetch", "origin", f"refs/heads/{branch}:refs/remotes/origin/{branch}"])
    git(["checkout", "--detach", f"refs/remotes/origin/{branch}"])
    git(["submodule", "sync", "--recursive"])
    git(["submodule", "update", "--init", "--recursive"])

    current_commit = git(["rev-parse", "HEAD"])
    source_already_merged = is_ancestor(source_commit, current_commit, ROOT)
    if not source_already_merged:
        result = subprocess.run(
            ["git", "merge", "--no-ff", "--no-commit", source_commit],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        conflicts = git(["diff", "--name-only", "--diff-filter=U"]).splitlines()
        allowed = {".gitmodules", "smarttubetv/build.gradle", "MediaServiceCore", "SharedModules"}
        unexpected = [item for item in conflicts if item not in allowed]
        if unexpected:
            raise RuntimeError(f"Unresolved RelayTube {channel} conflicts: {', '.join(unexpected)}")
        if result.returncode and not conflicts:
            details = (result.stderr or result.stdout).strip()
            raise RuntimeError(f"Could not merge SmartTube {channel} source: {details}")
        if ".gitmodules" in conflicts:
            git(["checkout", "--ours", ".gitmodules"])
            git(["add", ".gitmodules"])
        if "smarttubetv/build.gradle" in conflicts:
            preserve_relaytube_version_override()

    set_fork_module_urls()
    for name, commit in module_commits.items():
        path = ROOT / name
        if name == "MediaServiceCore":
            git(["submodule", "sync", "--recursive"], path)
            git(["submodule", "update", "--init", "--recursive"], path)
        current_module = git(["rev-parse", "HEAD"], path)
        if current_module != commit:
            git(["checkout", "--detach", commit], path)
            if name == "MediaServiceCore":
                git(["submodule", "sync", "--recursive"], path)
                git(["submodule", "update", "--init", "--recursive"], path)
        git(["add", name])

    git(["submodule", "sync", "--recursive"])
    merge_in_progress = subprocess.run(
        ["git", "rev-parse", "--verify", "-q", "MERGE_HEAD"],
        cwd=ROOT,
        text=True,
        capture_output=True,
    ).returncode == 0
    staged_changes = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=ROOT).returncode != 0
    if not staged_changes and not merge_in_progress:
        print(f"RelayTube {channel}: SmartTube and module sources are already current.")
        return False, current_commit

    if merge_in_progress:
        message = f"Merge SmartTube {channel} updates into RelayTube"
    else:
        message = f"Update RelayTube {channel} media modules"
    git(["commit", "-m", message])
    git(["push", "origin", f"HEAD:refs/heads/{branch}"])
    return True, git(["rev-parse", "HEAD"])


def upstream_version(source_commit: str) -> tuple[str, int]:
    contents = git(["show", f"{source_commit}:smarttubetv/build.gradle"])
    version_name = re.search(r'^\s*versionName\s+"([^"]+)"', contents, re.MULTILINE)
    version_code = re.search(r"^\s*versionCode\s+(\d+)", contents, re.MULTILINE)
    if version_name is None or version_code is None:
        raise RuntimeError(f"Could not read SmartTube version from {source_commit}")
    return version_name.group(1), int(version_code.group(1))


def stable_upstream_tag() -> str:
    releases = json.loads(
        run(
            [
                "gh",
                "release",
                "list",
                "--repo",
                SMARTTUBE_REPOSITORY,
                "--limit",
                "100",
                "--json",
                "tagName,isPrerelease,isDraft",
            ]
        )
    )
    for release in releases:
        if not release["isPrerelease"] and not release["isDraft"] and re.fullmatch(r"\d+\.\d+s", release["tagName"]):
            return release["tagName"]
    raise RuntimeError("SmartTube has no published stable release tag matching NN.NNs")


def upstream_module_ref(source_commit: str, name: str, channel: str) -> str:
    if channel == "beta":
        return "refs/heads/master"
    entry = git(["ls-tree", source_commit, name]).split()
    if len(entry) < 3 or entry[1] != "commit":
        raise RuntimeError(f"Could not read SmartTube stable {name} pin")
    return entry[2]


def release_manifest(tag: str) -> tuple[int, str | None]:
    with tempfile.TemporaryDirectory(prefix="relaytube-release-manifest-") as directory:
        result = subprocess.run(
            [
                "gh",
                "release",
                "download",
                tag,
                "--repo",
                RELAYTUBE_REPOSITORY,
                "--pattern",
                "relaytube-update.json",
                "--dir",
                directory,
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        manifest_path = Path(directory) / "relaytube-update.json"
        if result.returncode or not manifest_path.exists():
            return 0, None
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        versions = [
            (name, value["versionCode"])
            for name, value in manifest.items()
            if name != "package" and isinstance(value, dict) and isinstance(value.get("versionCode"), int)
        ]
        if not versions:
            raise RuntimeError(f"Release {tag} has no versionCode in relaytube-update.json")
        version_name, version_code = max(versions, key=lambda item: item[1])
        return version_code, version_name


def latest_stable_release_tag() -> str | None:
    releases = json.loads(
        run(
            [
                "gh",
                "release",
                "list",
                "--repo",
                RELAYTUBE_REPOSITORY,
                "--limit",
                "100",
                "--json",
                "tagName,isPrerelease,isDraft,isLatest",
            ]
        )
    )
    published = [release for release in releases if not release["isPrerelease"] and not release["isDraft"]]
    if not published:
        return None
    latest = next((release for release in published if release["isLatest"]), published[0])
    return latest["tagName"]


def suffixed_version(base: str, previous_name: str | None) -> str:
    match = re.fullmatch(re.escape(base) + r"\.(\d+)", previous_name or "")
    suffix = int(match.group(1)) + 1 if match else 1
    return f"{base}.{suffix}"


def set_output(name: str, value: str | bool) -> None:
    output = os.environ.get("GITHUB_OUTPUT")
    if not output:
        raise RuntimeError("GITHUB_OUTPUT is required")
    with open(output, "a", encoding="utf-8") as stream:
        stream.write(f"{name}={str(value).lower() if isinstance(value, bool) else value}\n")


def main() -> int:
    if not SYNC_TOKEN:
        raise RuntimeError(
            "Missing repository secret UPSTREAM_SYNC_TOKEN. Add a token with Contents read/write access to "
            "saappleg/MediaServiceCore and saappleg/SharedModules before weekly sync can publish."
        )
    if not os.environ.get("GH_TOKEN"):
        raise RuntimeError("GH_TOKEN is required to inspect release versions")

    upstream_remote = git(["remote", "get-url", "smarttube-upstream"], check=False)
    if upstream_remote:
        git(["remote", "set-url", "smarttube-upstream", UPSTREAM_ROOT_URL])
    else:
        git(["remote", "add", "smarttube-upstream", UPSTREAM_ROOT_URL])
    git(["fetch", "--no-tags", "smarttube-upstream", "refs/heads/master"])
    beta_source = git(["rev-parse", "FETCH_HEAD"])

    stable_tag = stable_upstream_tag()
    git(["fetch", "--no-tags", "smarttube-upstream", f"refs/tags/{stable_tag}"])
    stable_source = git(["rev-parse", "FETCH_HEAD"])

    module_commits: dict[str, dict[str, str]] = {"beta": {}, "stable": {}}
    for name, info in MODULES.items():
        for channel, source in (("beta", beta_source), ("stable", stable_source)):
            source_ref = upstream_module_ref(source, name, channel)
            module_commits[channel][name] = sync_module(ROOT / name, name, channel, source_ref)

    beta_changed, beta_commit = sync_app_channel("beta", beta_source, module_commits["beta"])
    stable_changed, stable_commit = sync_app_channel("stable", stable_source, module_commits["stable"])

    set_output("beta_changed", beta_changed)
    set_output("stable_changed", stable_changed)
    set_output("beta_source_ref", CHANNELS["beta"])
    set_output("stable_source_ref", CHANNELS["stable"])

    if beta_changed:
        upstream_name, upstream_code = upstream_version(beta_source)
        previous_code, previous_name = release_manifest("relaytube-beta")
        beta_name = suffixed_version(f"{upstream_name}-beta", previous_name)
        set_output("beta_version_name", beta_name)
        set_output("beta_version_code", max(upstream_code, previous_code + 1, 2435))
        set_output(
            "beta_release_notes",
            f"Synced SmartTube beta {upstream_name} ({beta_source[:12]}), MediaServiceCore, and SharedModules.",
        )
        print(f"RelayTube beta source: {beta_commit}")

    if stable_changed:
        upstream_name, upstream_code = upstream_version(stable_source)
        previous_tag = latest_stable_release_tag()
        previous_code, previous_name = release_manifest(previous_tag) if previous_tag else (0, None)
        stable_name = suffixed_version(stable_tag, previous_name)
        set_output("stable_version_name", stable_name)
        set_output("stable_version_code", max(upstream_code, previous_code + 1, 2435))
        set_output(
            "stable_release_notes",
            f"Synced SmartTube stable {stable_tag} ({stable_source[:12]}), MediaServiceCore, and SharedModules.",
        )
        print(f"RelayTube stable source: {stable_commit}")

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:  # report the failure in the GitHub Actions log
        print(f"SmartTube sync failed: {error}", file=sys.stderr)
        sys.exit(1)
