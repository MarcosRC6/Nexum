# NEXUM security policy

## Supported releases

Security fixes are prepared on a private development branch, audited, signed with the existing official key, and published only after explicit maintainer authorization.

## Report a vulnerability privately

Do **not** post passwords, signing material, access tokens, private backups or exploit details in a public issue, pull request or Actions log. Use GitHub's **private vulnerability reporting** under the repository Security tab if enabled. If that option is unavailable, open an issue containing only a non-sensitive request for a private contact channel.

## Signing and update trust

- Release signing is configured using GitHub Actions secrets, never committed key material.
- The production Android application ID is `com.example.estudario`.
- Official releases use the permanent signing certificate; do not replace or regenerate the signing key.
- Never accept release changes that disable independent signature, package ID and version checks.
- Publishing a GitHub Release requires `publish_release=true` through an explicitly triggered workflow.

## Security review before public release

Check **all** branches and history for secrets, review assets and copyrights, verify GitHub Actions logs and artifacts, configure branch protection after public conversion, and enable GitHub secret scanning / push protection. The `publication-preflight` CI job checks tracked current files only: it does not replace a full historical audit.

Keep any real user databases and backup ZIPs on personal devices, outside this repository.
