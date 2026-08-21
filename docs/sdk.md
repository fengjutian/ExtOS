# JavaScript SDK, draft 0

Plugin packages include the current development SDK file themselves. A future
CLI will inject a versioned SDK during build.

```html
<script src="extos-sdk.js"></script>
```

The script exposes an immutable `window.ext` API:

```js
const runtime = await ext.runtime.version();
await ext.ui.toast(`Runtime ${runtime.version}`);

await ext.storage.set('settings', { theme: 'dark' });
const settings = await ext.storage.get('settings');
await ext.storage.remove('settings');
await ext.storage.clear();
```

Bridge calls time out after 15 seconds. Lifecycle subscriptions return an
unsubscribe function:

```js
const unsubscribe = ext.lifecycle.on('suspend', () => saveDraft());
```

Available lifecycle names are `ready`, `resume`, `suspend`, and `shutdown`.
Storage is private to a plugin ID, persists across version upgrades, and is
currently limited to 1 MiB of encoded JSON.
