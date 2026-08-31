## Summary

All eight P0a/P0b binary provenance probes passed on this host, with zero discrepancies against the round-0 review's receipts and zero internal inconsistencies. The mpv runtime shipped in `app-full-arm64-v8a-debug.apk` is byte-identical to the `arm64-v8a/libmpv.so` inside the cached `io.github.abdallahmehiz:mpv-android-lib:0.1.12` AAR (SHA-256 `ea485887c3663a69c7ea9586530e173fda8d498f67891e29909ddcf06bf19f16`), self-identifies as mpv `v0.41.0-174-g76a5eba99`, and contains the `audio-add`/`audio-remove`/`audio-reload` command strings the external-audio adapter needs. The local Media3 fork self-identifies as exactly `1.8.0` (`AndroidXMedia3/1.8.0`). The APK was the `fullDebug` variant (versionCode 1051, applicationId `com.nuviodebug.com`) built from dev HEAD `3ca77f44` on 2026-09-01.

## Probe table

| # | Probe | Result | Evidence |
|---|-------|--------|----------|
| P0a-1 | Locate mpv AAR in Gradle cache; verify coordinates | PASS | `/Users/ffrappo/.gradle/caches/modules-2/files-2.1/io.github.abdallahmehiz/mpv-android-lib/0.1.12/dc39f049f70af00140214eeb925f0a90308ea4e7/mpv-android-lib-0.1.12.aar`; coordinates declared at `app/build.gradle.kts:496` |
| P0a-2 | SHA-256 of AAR | PASS | `bb1a007c545cc7ac3304293ae79866b5361a48449ee7648c4030d5355869effc` (equals review's `bb1a007c…989effc`) |
| P0a-3 | Extract `jni/arm64-v8a/libmpv.so`, SHA-256 | PASS | `ea485887c3663a69c7ea9586530e173fda8d498f67891e29909ddcf06bf19f16`; ELF 64-bit ARM aarch64, stripped (extracted to task scratch, repo/cache untouched) |
| P0a-4 | mpv version string in binary | PASS | unique match `v0.41.0-174-g76a5eba99` (`strings -a` and `grep -aoE 'v0\.4[0-9]…'` agree; single occurrence) |
| P0a-5 | `audio-add` / `audio-remove` / `audio-reload` strings | PASS | all three present as standalone NUL-terminated strings (strings lines 14391 / 7951 / 16436) |
| P0b-6 | SHA-256 of all 13 AARs in `app/libs/` | PASS | recorded in `probe-receipt.json` `appLibsSha256` (SBOM receipt) |
| P0b-7 | Media3 version from `lib-common-release.aar` | PASS | `MediaLibraryInfo.class` constant pool: `VERSION "1.8.0"`, `VERSION_SLASHY "AndroidXMedia3/1.8.0"` |
| P0b-8 | APK `lib/arm64-v8a/libmpv.so` cross-check | PASS | `unzip -p …apk lib/arm64-v8a/libmpv.so \| shasum -a 256` → `ea4858…bf19f16`, **identical** to the AAR-internal hash |

## Discrepancy callouts

- **None material.** Every hash matches the review's prior receipt exactly; the APK-internal and AAR-internal `libmpv.so` hashes are equal, so the shipped binary is proven to be the audited dependency.
- **Metadata nit (task premise vs repo):** the task said to expect the coordinates "per `gradle/libs.versions.toml`", but the toml contains no mpv entry — `io.github.abdallahmehiz:mpv-android-lib:0.1.12` is declared directly at `app/build.gradle.kts:496`. The resolved cache path and directory hash (`dc39f049…`) match the review regardless, so this is a documentation-locus nit, not a provenance discrepancy.
- **Tooling caveat (methodology, not result):** macOS `strings` refuses Java class files because `0xCAFEBABE` collides with the Mach-O fat-binary magic; Media3 constants were therefore read via `grep -a` over the extracted `MediaLibraryInfo.class` bytes, which shows the full constant pool unambiguously.
- Static string presence is necessary-but-not-sufficient, per the review: runtime confirmation of `audio-add`/`remove`/`reload` behavior still requires the on-device probes P1–P3.

## Implications for the MPV artifact pilot

The identity premise of the pilot is now receipted: any adapter written against `mpv-android-lib:0.1.12` (mpv `v0.41.0-174-g76a5eba99`) targets the exact binary that ships, with the required external-audio command surface present in the binary, and Media3-side experiments must be validated against the fork's `1.8.0` behavior rather than current upstream documentation. The remaining risk is entirely dynamic — attachment durability across rebuild/loadfile paths, auxiliary-error classification, and header isolation — so the P1–P5 runtime probes should proceed on this pinned baseline before any wrapper code merges.
