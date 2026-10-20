# PR 2: secure credential storage (incremental)

This branch introduces the encrypted persistence layer, **not** a complete multi-user OAuth flow.

- Set `CREATORRX_TOKEN_ENCRYPTION_KEY` to a randomly generated, base64-encoded 32-byte key in database mode.
- Never commit the key. Back it up securely: losing it makes existing tokens undecryptable.
- Refresh tokens use AES-256-GCM with a fresh 96-bit nonce and creator UUID as authenticated associated data.
- A creator's encrypted token cannot be decrypted with another creator's UUID.
- Credentials are upserted per creator and can be deleted on disconnect.
- `key_version` is reserved for future rotation; rotation logic is not yet implemented.

**Security gate:** The original `GoogleGateway` remains a single-account in-memory POC. Do not expose the persistence APIs or claim multi-user authentication until verified OIDC identity, creator-scoped API calls, concurrent refresh, session ownership and callback wiring are implemented and tested.
