# CallGuard Keys

Private tool for the seller: makes activation codes for CallGuard customers. Keep the APK on your own phone only.

## Build
1. Create a GitHub repo and push this folder to `main`.
2. Open **Actions**, wait for "Build Keys APK", then download the **CallGuard-Keys-apk** artifact.
3. Install `app-debug.apk` on your phone.

## Use
1. First launch: paste your private key (from `PRIVATE_KEY_keep_secret.txt`). It stays on this phone.
2. Paste the customer's Device ID (or their whole message), tap **Generate code**, then **Share**.

The private key must match the public key inside the CallGuard app. Never put the private key in GitHub.
