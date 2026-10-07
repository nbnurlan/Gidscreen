# Gidscreen Development Rules

## Architecture
- Preserve the existing MediaProjection flow.
- Do not add Accessibility permission unless it is explicitly required and justified.
- Keep Gemini and Hugging Face integrations in separate service-layer components.
- Do not mix API-provider logic into UI code.

## State & UX
- Preserve chat history.
- Preserve process-death state restoration.
- Account for rotation and Android lifecycle changes.
- Restore floating-overlay state when practical and safe.
- A new screen selection must not destroy the active chat session.

## Security
- Never hardcode API tokens or secrets in source code.
- Never log API tokens or secrets.
- Do not commit secret/key files to GitHub.
- Do not add unnecessary Android permissions.

## Development Workflow
- Perform every substantial feature or fix on a separate branch.
- Prefer minimal, targeted changes.
- Avoid unrelated refactoring.
- Inspect the existing implementation and data flow before changing working behavior.

## Testing
- Test both the primary flow and relevant edge cases for every feature.
- Consider permission denial, rotation, process death, API failures, large payloads, and retry behavior where applicable.
- Build and existing tests must pass before merging.
- Do not merge into `main` while CI/build/tests are failing.

## Git
- Do not make substantial changes directly on `main`.
- Use: branch -> commit -> pull request -> CI -> review -> merge.
- Review the PR diff and CI result before merging.
