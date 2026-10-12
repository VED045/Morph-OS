# Self-hosted theme community

The Android community screen talks to the included FastAPI backend. Nothing is uploaded until the user taps Upload. Only bounded declarative theme settings are accepted: no scripts, app lists, contacts, notification data, model files or media.

1. Install the backend as described in the main README.
2. Set `COMMUNITY_DATABASE=data/community.sqlite` in backend `.env`. Use a persistent volume in a container. Empty disables the community.
3. Generate a strong `COMMUNITY_INVITE` and a separate `MORPHOS_CLIENT_TOKEN`. The latter is the administrator/AI-client credential; **do not give it to community creators**. Give invited creators only the community invite. Rotate the invite to close registration.
4. Start FastAPI and place it behind a valid HTTPS reverse proxy. Android uses normal system certificate verification for the community endpoint. This repository does not deploy a public service automatically.
5. Creators register from Android and receive an individual random key. The database stores only its SHA-256 digest. Creator keys stay in Android screen memory and must be saved by the user before leaving. There is no password recovery; an administrator must manage lost accounts in this MVP.
6. In the backend `/docs` API explorer, review pending submissions using `GET /community/review` with `Authorization: Bearer <administrator-token>`. Inspect the JSON, then call `POST /community/review/{id}/approve` or `/reject`. Never paste real keys into public issues.
7. The public `GET /community/themes` returns only approved themes, 40 at a time. Pass the returned `next` as `before` to load the next page. Android offers Next page.
8. An owner can withdraw a submission with `DELETE /community/themes/{id}` and their creator bearer key. Administrators can reject/remove published entries using the same review endpoint.

Uploads are capped at five per creator per day; storage caps are 500 creators and 5,000 theme records. Request bodies and schema bounds are checked before persistence. SQLite uses parameterized queries and write transactions. Back up the database and configure HTTPS, reverse-proxy rate limits, administrator access and operational moderation before inviting a broad public audience. This is an invite-based community MVP, not a production social network with account recovery, ratings, comments or copyrighted asset hosting.
