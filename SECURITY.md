# Security Policy

## Supported Versions

Security fixes are developed for the latest source on the main branch. Older source revisions and APKs built from them may not receive fixes; use a build made from the latest reviewed source when possible.

| Version | Security updates |
| --- | --- |
| Latest main | Supported |
| Older revisions and locally built APKs | Not guaranteed |

## Reporting a Vulnerability

Please report suspected vulnerabilities privately. If private vulnerability reporting is enabled for this repository, use GitHub's **Report a vulnerability** flow under the repository's **Security** tab. Otherwise, contact the repository maintainer privately through GitHub. Do not open a public issue or publish exploit details before the maintainer has had a reasonable opportunity to investigate and coordinate a fix.

Include, when safe to do so:

- The affected app version or source commit.
- Android version and device model.
- The security impact and steps to reproduce, using test data.
- Relevant logs with secrets and personal information removed.

**Do not send real photos, videos, notes, PINs, recovery codes, vault archives, signing keys, or passwords.** A sanitized reproduction or a newly created test vault is sufficient. Do not upload private vault contents to an issue or attach them to a report.

There is no bug bounty or guaranteed response time. The maintainer will acknowledge reports when able, investigate, and coordinate disclosure after a fix or mitigation is available.

## Scope

Brankas is a local-only Android application without an account, cloud sync, analytics, or a project-operated server. Reports are especially relevant to vault encryption and key handling, PIN or recovery flows, biometric unlock, import and restore integrity, encrypted backup handling, and unintended exposure of private data by the app.

The Android operating system, a compromised or rooted device, and third-party document or backup providers are outside the project's control. Please still report a concrete issue if Brankas itself mishandles data or fails to protect it while interacting with those components.

Brankas supports user-initiated encrypted backup export. Anyone handling a backup should treat it as sensitive: it contains encrypted vault files and is intended to be opened with the vault's PIN or recovery code. Choose a trusted destination and never include a real backup in a public report.