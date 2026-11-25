#!/usr/bin/env python3
"""
JARVIS AI - Android build script (pure framework Java, no Gradle)
Pipeline: aapt2 → javac → d8 → zipalign → apksigner
Following the hisaab proven pipeline.
"""
import os
import sys
import subprocess
import zipfile
import datetime

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "src")
RES = os.path.join(BASE, "res")
LIBS = os.path.join(BASE, "libs")
BUILD = os.path.join(BASE, "build")
D8_JAR = os.path.join(BASE, "tools", "d8-9.2.4.jar")
APK_OUT = os.path.join(BASE, "build", "jarvis-release.apk")
AAB_OUT = os.path.join(BASE, "build", "jarvis-release.aab")
KEYSTORE = os.path.join(BASE, "signing", "keystore.jks")
KEY_ALIAS = "jarvis"
KEY_PASSWORD = "jarvis123"

def die(msg):
    print("❌ " + msg, file=sys.stderr)
    sys.exit(1)

def run(cmd, **kw):
    print("> " + " ".join(cmd) if isinstance(cmd, list) else cmd)
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        die("Command failed: " + (" ".join(cmd) if isinstance(cmd, list) else cmd))
    return r

def which(name):
    import shutil
    return shutil.which(name)

def main():
    print("=" * 60)
    print("JARVIS AI — Android build (pure framework, no Gradle)")
    print("=" * 60)

    # ---- Check tools ----
    for t in ["aapt2", "javac", "d8", "apksigner"]:
        if which(t) is None:
            die(f"{t} not found in PATH. Install it first.")

    if not os.path.exists(D8_JAR):
        die(f"d8 jar not found at {D8_JAR}")

    if not os.path.exists(KEYSTORE):
        die(f"Keystore not found at {KEYSTORE}. Create one first.")

    # ---- Create build dirs ----
    print("\nBuilding JARVIS AI Android app...")
    work = os.path.join(BUILD, "work")
    os.makedirs(work, exist_ok=True)

    android_jar = os.path.join(LIBS, "android.jar")
    if not os.path.exists(android_jar):
        die("android.jar not found in libs/")

    # ---- Step 1: aapt2 compile resources ----
    print("\n--- Step 1: aapt2 compile resources ---")
    res_zip = os.path.join(work, "res.zip")
    run(["aapt2", "compile", "--dir", RES, "-o", res_zip])

    # ---- Step 2: aapt2 link ----
    print("\n--- Step 2: aapt2 link (generate R.java, package APK skeleton) ---")
    gen_dir = os.path.join(work, "gen")
    os.makedirs(gen_dir, exist_ok=True)
    run(["aapt2", "link", "-o", os.path.join(work, "base.apk"),
         "-I", android_jar,
         "--manifest", os.path.join(BASE, "AndroidManifest.xml"),
         "--resources", res_zip,
         "--java", gen_dir,
         "--auto-add-overlay",
         "--min-sdk-version", "26",
         "--target-sdk-version", "34",
         "--version-code", "1",
         "--version-name", "1.0.0"]

    # ---- Step 3: javac compile (including generated R.java) ----
    print("\n--- Step 3: javac compile ---")
    classes_dir = os.path.join(work, "classes")
    os.makedirs(classes_dir, exist_ok=True)

    # Collect all Java source files: src/ + generated gen/
    all_java = []
    # src recursive
    for root, dirs, files in os.walk(SRC):
        for f in files:
            if f.endswith(".java"):
                all_java.append(os.path.join(root, f))
    # generated R.java
    generated_r = os.path.join(gen_dir, "com", "jarvisai", "app", "R.java")
    if os.path.exists(generated_r):
        all_java.append(generated_r)
    else:
        print("⚠️  Generated R.java not found at expected path, searching...")
        for root, dirs, files in os.walk(gen_dir):
            for f in files:
                if f == "R.java":
                    path = os.path.join(root, f)
                    try:
                        with open(path) as rf:
                            content = rf.read()
                        if "com.jarvisai.app" in content:
                            all_java.append(path)
                    except: pass

    if not all_java:
        die("No Java source files found. Check src/ layout.")

    print(f"Found {len(all_java)} Java source files to compile")

    run(["javac", "--release", "8",
         "-classpath", android_jar,
         "-d", classes_dir,
         "-encoding", "UTF-8",
         "-nowarn",
         "-source", "8",
         "-target", "8"] + all_java)

    # ---- Step 4: d8 dex conversion ----
    print("\n--- Step 4: d8 dex conversion ---")
    dex_out = os.path.join(work, "classes.dex")
    run(["java", "-jar", D8_JAR,
         "--release",
         "--min-api", "26",
         "--output", dex_out,
         classes_dir])

    # ---- Step 5: package APK (zip the dex into base.apk) ----
    print("\n--- Step 5: package APK ---")
    base_apk = os.path.join(work, "base.apk")
    # Copy the aapt2-linked base.apk and replace/add classes.dex
    import shutil
    if os.path.exists(os.path.join(work, "base.apk")):
        shutil.copy2(os.path.join(work, "base.apk"), base_apk)
    else:
        # Create minimal APK skeleton
        with zipfile.ZipFile(base_apk, 'w', zipfile.ZIP_STORED) as zout:
            zout.writestr("AndroidManifest.xml", open(os.path.join(BASE, "AndroidManifest.xml")).read())
            zout.writestr("classes.dex", b"")  # placeholder

    # Insert classes.dex at the right place
    # Read the base.apk, find and replace classes.dex, or add it
    # Simpler: just add it as an extra file
    with zipfile.ZipFile(base_apk, 'a', zipfile.ZIP_STORED) as zout:
        zout.write(dex_out, 'classes.dex')

    # ---- Step 6: sign with apksigner ----
    print("\n--- Step 6: sign APK ---")
    pw_file = os.path.join(work, "keypass.txt")
    with open(pw_file, "w") as f:
        f.write(KEY_PASSWORD)

    run(["apksigner", "sign",
         "--ks", KEYSTORE,
         "--ks-alias", KEY_ALIAS,
         "--ks-pass", "file:" + pw_file,
         "--out", APK_OUT,
         base_apk])

    print(f"\n✅ APK built: {APK_OUT}")

    # ---- AAB generation (if bundletool available) ----
    print("\n--- AAB generation ---")
    if which("bundletool") is not None:
        run(["bundletool", "build-apks",
             "--output=" + AAB_OUT,
             "--apk=" + APK_OUT])
        print(f"✅ AAB built: {AAB_OUT}")
    else:
        print("bundletool not found — skipping AAB.")
        print("To generate AAB: pip install bundletool OR download from GitHub.")
        print("  bundletool build-apks --apks=" + APK_OUT)

    print("\n" + "=" * 60)
    print("Build complete!")
    print("Required user configuration:")
    print("  1. Get Gemini API key at: aistudio.google.com/apikey")
    print("  2. Add it in JARVIS → Settings → API key")
    print("  3. Grant all permissions on first launch")
    print("  4. Grant 'Display over other apps' for floating orb")
    print("=" * 60)

if __name__ == "__main__":
    main()