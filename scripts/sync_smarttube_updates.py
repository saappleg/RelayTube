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


ROOT = Path(os.environ.get("GITHUB_WORKSPACE", Path(__file__).resolve().parents[1])).resolve()
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
ERROR_FIXER_CONTROLLER_PATH = (
    "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/"
    "playback/controllers/ErrorFixerController.java"
)
EXO_PLAYER_CONTROLLER_PATH = (
    "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/"
    "controller/ExoPlayerController.java"
)
RELAYTUBE_LAYOUT_PATHS = (
    "smarttubetv/src/main/res/layout/leanback_list_preference_item_single.xml",
    "smarttubetv/src/main/res/layout/leanback_preference.xml",
)
SYNC_TOKEN = os.environ.get("UPSTREAM_SYNC_TOKEN", "")


def run(args: list[str], cwd: Path = ROOT, *, check: bool = True) -> str:
    process = subprocess.run(args, cwd=cwd, text=True, capture_output=True)
    if check and process.returncode:
        details = (process.stderr or process.stdout).strip()
        raise RuntimeError(f"{args[0]} command failed ({process.returncode}): {details}")
    return process.stdout.strip()


def git(args: list[str], cwd: Path = ROOT, *, check: bool = True) -> str:
    return run(["git", *args], cwd, check=check)


def configure_git_identity(cwd: Path = ROOT) -> None:
    git(["config", "user.name", "github-actions[bot]"], cwd)
    git(
        [
            "config",
            "user.email",
            "41898282+github-actions[bot]@users.noreply.github.com",
        ],
        cwd,
    )


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
    configure_git_identity(path)
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


def replace_exactly_once(contents: str, old: str, new: str, description: str) -> str:
    if contents.count(old) != 1:
        raise RuntimeError(f"Unexpected {description}; refusing automatic conflict resolution")
    return contents.replace(old, new, 1)


def git_file_contents(revision: str, path: str, source_name: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{revision}:{path}"],
        cwd=ROOT,
        capture_output=True,
    )
    if result.returncode:
        details = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"Could not read {source_name} {path}: {details}")
    return result.stdout.decode("utf-8")


def merge_relaytube_error_fixer_contents(upstream_contents: str, relaytube_contents: str) -> str:
    newline = "\r\n" if "\r\n" in upstream_contents else "\n"
    contents = upstream_contents.replace("\r\n", "\n")
    relaytube = relaytube_contents.replace("\r\n", "\n")

    dns_pattern = re.compile(
        r"(?m)^([ \t]*)int customDns = PlayerTweaksData\.(DNS_TYPE_[A-Z0-9_]+);$"
    )
    upstream_dns = dns_pattern.findall(contents)
    relaytube_dns = dns_pattern.findall(relaytube)
    if len(upstream_dns) != 1 or len(relaytube_dns) != 1:
        raise RuntimeError(
            "Unexpected ErrorFixerController DNS setting; refusing automatic conflict resolution"
        )
    relaytube_dns_type = relaytube_dns[0][1]
    contents, replacements = dns_pattern.subn(
        lambda match: (
            f"{match.group(1)}int customDns = PlayerTweaksData.{relaytube_dns_type};"
        ),
        contents,
        count=1,
    )
    if replacements != 1:
        raise RuntimeError("Could not preserve RelayTube ErrorFixerController DNS setting")

    fallback = (
        "        } else {\n"
        "            // NOTE: The bug. Avoid calling reloadVideo() after lowering the quality.\n"
    )
    if contents.count(fallback) != 1:
        raise RuntimeError(
            "Unexpected upstream ErrorFixerController layout; refusing automatic conflict resolution"
        )

    relaytube_recovery = """        } else if (!isOfflineVideo() && !mBufferingDetector.isPlayable()) {
            if (getPlayerTweaksData().getPlayerDataSource() != PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP
                    && getPlayerTweaksData().getPreferredDnsType() != PlayerTweaksData.DNS_TYPE_SYSTEM
                    && !getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
                // Preserve RelayTube's custom-DNS recovery for online playback.
                MessageHelpers.showLongMessage(getContext(), "Fixing wrong DNS resolving...");
                getPlayerTweaksData().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
                mVideoLoaderController.restartEngine();
            } else {
                // Some clients may just hang at the video start
                MessageHelpers.showLongMessage(getContext(), "Fixing stalled client...");
                YouTubeServiceManager.instance().switchNextClientNow();
                mVideoLoaderController.reloadVideo();
            }
"""
    contents = contents.replace(fallback, relaytube_recovery + fallback, 1)
    return contents.replace("\n", "\r\n") if newline == "\r\n" else contents


def resolve_relaytube_error_fixer_conflict(source_commit: str) -> None:
    upstream = git_file_contents(source_commit, ERROR_FIXER_CONTROLLER_PATH, "upstream")
    relaytube = git_file_contents("HEAD", ERROR_FIXER_CONTROLLER_PATH, "RelayTube")
    contents = merge_relaytube_error_fixer_contents(upstream, relaytube)
    destination = ROOT / ERROR_FIXER_CONTROLLER_PATH
    with destination.open("w", encoding="utf-8", newline="") as output:
        output.write(contents)
    git(["add", ERROR_FIXER_CONTROLLER_PATH])
    print("Preserved RelayTube DNS recovery in ErrorFixerController.java.")


def merge_relaytube_exoplayer_diagnostics(upstream_contents: str, relaytube_contents: str) -> str:
    newline = "\r\n" if "\r\n" in upstream_contents else "\n"
    contents = upstream_contents.replace("\r\n", "\n")
    relaytube = relaytube_contents.replace("\r\n", "\n")
    diagnostic_markers = (
        "mPrepareStartedAtMs",
        "Playback startup: prepare requested",
        "Playback startup: ready in ",
        "elapsedSincePrepare(long nowMs)",
    )
    if not all(marker in relaytube for marker in diagnostic_markers):
        raise RuntimeError(
            "RelayTube ExoPlayer startup diagnostics changed; refusing automatic conflict resolution"
        )
    if any(marker in contents for marker in diagnostic_markers):
        if all(marker in contents for marker in diagnostic_markers):
            return contents.replace("\n", "\r\n") if newline == "\r\n" else contents
        raise RuntimeError(
            "Upstream ExoPlayer startup diagnostics are partial; refusing automatic conflict resolution"
        )

    contents = replace_exactly_once(
        contents,
        "import android.os.Build.VERSION;\n",
        "import android.os.Build.VERSION;\nimport android.os.SystemClock;\n",
        "ExoPlayerController imports",
    )
    fields = (
        "    private long mPrepareStartedAtMs;\n"
        "    private long mFirstBufferingAtMs;\n"
        "    private long mFirstTracksAtMs;\n"
        "    private long mFirstLoadingFinishedAtMs;\n"
        "    private boolean mStartupReported;\n"
    )
    contents = replace_exactly_once(
        contents,
        "    private Runnable mOnVideoLoaded;\n",
        "    private Runnable mOnVideoLoaded;\n" + fields,
        "ExoPlayerController diagnostic fields",
    )
    contents = replace_exactly_once(
        contents,
        "        mSabrLiveManifest = null; // set by the sabr live only\n"
        "        mSabrLiveSeekBlocked = false;\n"
        "        mSabrLiveStartPending = false;\n",
        "        mSabrLiveManifest = null; // set by the sabr live only\n"
        "        mSabrLiveSeekBlocked = false;\n"
        "        mSabrLiveStartPending = false;\n"
        "        mPrepareStartedAtMs = SystemClock.elapsedRealtime();\n"
        "        mFirstBufferingAtMs = 0;\n"
        "        mFirstTracksAtMs = 0;\n"
        "        mFirstLoadingFinishedAtMs = 0;\n"
        "        mStartupReported = false;\n",
        "ExoPlayerController source reset",
    )
    contents = replace_exactly_once(
        contents,
        "        mEventListener.onSourceChanged(getVideo());\n",
        "        mEventListener.onSourceChanged(getVideo());\n"
        "        Log.d(TAG, \"Playback startup: prepare requested\");\n",
        "ExoPlayerController prepare log",
    )
    contents = replace_exactly_once(
        contents,
        '        Log.d(TAG, "onTracksChanged: start: groups length: " + trackGroups.length);\n',
        "        if (mPrepareStartedAtMs > 0 && mFirstTracksAtMs == 0) {\n"
        "            mFirstTracksAtMs = SystemClock.elapsedRealtime();\n"
        "            Log.d(TAG, \"Playback startup: tracks in \" + elapsedSincePrepare(mFirstTracksAtMs) + \"ms\");\n"
        "        }\n"
        '        Log.d(TAG, "onTracksChanged: start: groups length: " + trackGroups.length);\n',
        "ExoPlayerController track log",
    )
    contents = replace_exactly_once(
        contents,
        '        Log.e(TAG, "onPlayerError: " + error);\n',
        "        Log.e(TAG, \"Playback startup: failed after \" + elapsedSincePrepare(SystemClock.elapsedRealtime()) + \"ms: \" + error);\n"
        '        Log.e(TAG, "onPlayerError: " + error);\n',
        "ExoPlayerController error log",
    )
    buffering_marker = (
        "        boolean isBuffering = Player.STATE_BUFFERING == playbackState && playWhenReady;\n"
    )
    startup_metrics = """        if (isBuffering && mPrepareStartedAtMs > 0 && mFirstBufferingAtMs == 0) {
            mFirstBufferingAtMs = SystemClock.elapsedRealtime();
            Log.d(TAG, "Playback startup: buffering in " + elapsedSincePrepare(mFirstBufferingAtMs) + "ms");
        }

        if (isPlayPressed && mPrepareStartedAtMs > 0 && !mStartupReported) {
            mStartupReported = true;
            long now = SystemClock.elapsedRealtime();
            String buffering = mFirstBufferingAtMs > 0
                    ? String.valueOf(mFirstBufferingAtMs - mPrepareStartedAtMs)
                    : "not observed";
            String tracks = mFirstTracksAtMs > 0
                    ? String.valueOf(mFirstTracksAtMs - mPrepareStartedAtMs)
                    : "not observed";
            String loadingFinished = mFirstLoadingFinishedAtMs > 0
                    ? String.valueOf(mFirstLoadingFinishedAtMs - mPrepareStartedAtMs)
                    : "still loading";
            long bufferedAheadMs = mPlayer != null
                    ? Math.max(0, mPlayer.getBufferedPosition() - mPlayer.getCurrentPosition())
                    : 0;
            Log.d(TAG, "Playback startup: ready in " + elapsedSincePrepare(now)
                    + "ms (buffering=" + buffering + "ms, tracks=" + tracks
                    + "ms, loading=" + loadingFinished + "ms, bufferedAhead=" + bufferedAheadMs + "ms)");
        }

"""
    contents = replace_exactly_once(
        contents,
        buffering_marker,
        buffering_marker + startup_metrics,
        "ExoPlayerController state metrics",
    )
    loading_method = """    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (!isLoading && mPrepareStartedAtMs > 0 && mFirstLoadingFinishedAtMs == 0) {
            mFirstLoadingFinishedAtMs = SystemClock.elapsedRealtime();
            Log.d(TAG, "Playback startup: initial loading finished in "
                    + elapsedSincePrepare(mFirstLoadingFinishedAtMs) + "ms");
        }
    }

    private long elapsedSincePrepare(long nowMs) {
        return mPrepareStartedAtMs > 0 ? Math.max(0, nowMs - mPrepareStartedAtMs) : 0;
    }

"""
    if "public void onLoadingChanged(boolean isLoading)" in contents:
        raise RuntimeError(
            "Upstream ExoPlayer loading callback changed; refusing automatic conflict resolution"
        )
    contents = replace_exactly_once(
        contents,
        "    @Override\n    public void onPositionDiscontinuity(int reason) {\n",
        loading_method + "    @Override\n    public void onPositionDiscontinuity(int reason) {\n",
        "ExoPlayerController loading callback",
    )
    return contents.replace("\n", "\r\n") if newline == "\r\n" else contents


def resolve_relaytube_exoplayer_conflict(source_commit: str) -> None:
    upstream = git_file_contents(source_commit, EXO_PLAYER_CONTROLLER_PATH, "upstream")
    relaytube = git_file_contents("HEAD", EXO_PLAYER_CONTROLLER_PATH, "RelayTube")
    contents = merge_relaytube_exoplayer_diagnostics(upstream, relaytube)
    destination = ROOT / EXO_PLAYER_CONTROLLER_PATH
    with destination.open("w", encoding="utf-8", newline="") as output:
        output.write(contents)
    git(["add", EXO_PLAYER_CONTROLLER_PATH])
    print("Preserved RelayTube playback startup diagnostics with upstream SABR live support.")


def merge_relaytube_layout_contents(upstream_contents: str, relaytube_contents: str, path: str) -> str:
    upstream_ids = re.findall(r'\bandroid:id="([^"]+)"', upstream_contents)
    relaytube_ids = re.findall(r'\bandroid:id="([^"]+)"', relaytube_contents)
    if not upstream_ids or sorted(upstream_ids) != sorted(relaytube_ids):
        raise RuntimeError(
            f"Upstream preference layout IDs changed in {path}; refusing automatic conflict resolution"
        )
    return relaytube_contents


def resolve_relaytube_layout_conflict(source_commit: str, path: str) -> None:
    upstream = git_file_contents(source_commit, path, "upstream")
    relaytube = git_file_contents("HEAD", path, "RelayTube")
    contents = merge_relaytube_layout_contents(upstream, relaytube, path)
    destination = ROOT / path
    with destination.open("w", encoding="utf-8", newline="") as output:
        output.write(contents)
    git(["add", path])
    print(f"Preserved RelayTube preference styling in {Path(path).name}.")


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
    configure_git_identity()
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
        allowed = {
            ".gitmodules",
            "smarttubetv/build.gradle",
            "MediaServiceCore",
            "SharedModules",
            ERROR_FIXER_CONTROLLER_PATH,
            EXO_PLAYER_CONTROLLER_PATH,
            *RELAYTUBE_LAYOUT_PATHS,
        }
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
        if ERROR_FIXER_CONTROLLER_PATH in conflicts:
            resolve_relaytube_error_fixer_conflict(source_commit)
        if EXO_PLAYER_CONTROLLER_PATH in conflicts:
            resolve_relaytube_exoplayer_conflict(source_commit)
        for path in RELAYTUBE_LAYOUT_PATHS:
            if path in conflicts:
                resolve_relaytube_layout_conflict(source_commit, path)

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


def release_contains_sources(tag: str, source_commits: list[str]) -> bool:
    result = subprocess.run(
        [
            "gh",
            "release",
            "view",
            tag,
            "--repo",
            RELAYTUBE_REPOSITORY,
            "--json",
            "body",
        ],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if result.returncode:
        details = (result.stderr or result.stdout).strip()
        if "release not found" in details.lower():
            return False
        raise RuntimeError(f"Could not inspect RelayTube release {tag}: {details}")
    body = json.loads(result.stdout).get("body") or ""
    return all(commit[:12] in body for commit in source_commits)


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

    beta_upstream_name, beta_upstream_code = upstream_version(beta_source)
    beta_previous_code, beta_previous_name = release_manifest("relaytube-beta")
    beta_release_sources = [
        beta_commit,
        beta_source,
        module_commits["beta"]["MediaServiceCore"],
        module_commits["beta"]["SharedModules"],
    ]
    beta_needs_release = beta_changed or not release_contains_sources(
        "relaytube-beta", beta_release_sources
    )

    previous_stable_tag = latest_stable_release_tag()
    stable_previous_code, stable_previous_name = (
        release_manifest(previous_stable_tag) if previous_stable_tag else (0, None)
    )
    stable_release_sources = [
        stable_commit,
        stable_source,
        module_commits["stable"]["MediaServiceCore"],
        module_commits["stable"]["SharedModules"],
    ]
    stable_needs_release = stable_changed or not (
        previous_stable_tag
        and release_contains_sources(previous_stable_tag, stable_release_sources)
    )

    set_output("beta_changed", beta_needs_release)
    set_output("stable_changed", stable_needs_release)
    set_output("beta_source_ref", CHANNELS["beta"])
    set_output("stable_source_ref", CHANNELS["stable"])

    if beta_needs_release:
        beta_name = suffixed_version(f"{beta_upstream_name}-beta", beta_previous_name)
        set_output("beta_version_name", beta_name)
        set_output("beta_version_code", max(beta_upstream_code, beta_previous_code + 1, 2435))
        set_output(
            "beta_release_notes",
            f"RelayTube beta {beta_commit[:12]} synced SmartTube beta {beta_upstream_name} "
            f"({beta_source[:12]}), MediaServiceCore "
            f"{module_commits['beta']['MediaServiceCore'][:12]}, and SharedModules "
            f"{module_commits['beta']['SharedModules'][:12]}.",
        )
        print(f"RelayTube beta source: {beta_commit}")

    if stable_needs_release:
        stable_name = suffixed_version(stable_tag, stable_previous_name)
        set_output("stable_version_name", stable_name)
        set_output(
            "stable_version_code",
            max(upstream_version(stable_source)[1], stable_previous_code + 1, 2435),
        )
        set_output(
            "stable_release_notes",
            f"RelayTube stable {stable_commit[:12]} synced SmartTube stable {stable_tag} "
            f"({stable_source[:12]}), MediaServiceCore "
            f"{module_commits['stable']['MediaServiceCore'][:12]}, and SharedModules "
            f"{module_commits['stable']['SharedModules'][:12]}.",
        )
        print(f"RelayTube stable source: {stable_commit}")

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:  # report the failure in the GitHub Actions log
        print(f"SmartTube sync failed: {error}", file=sys.stderr)
        sys.exit(1)
