# Controlled network capability

Plugins cannot use direct WebView network requests. A plugin requesting
`network.fetch` must also declare exact hosts:

```json
{
  "capabilities": ["network.fetch"],
  "networkAllowlist": ["api.example.com"]
}
```

Draft 0 supports HTTPS GET only:

```js
const response = await ext.network.fetch('https://api.example.com/v1/items');
```

The runtime requires an exact hostname match and standard HTTPS port. It rejects
URL credentials, fragments, redirects, private/local/multicast destinations,
responses larger than 1 MiB, and requests exceeding connection/read timeouts.
Only status, content type, and a UTF-8 body are returned.

DNS resolution is checked before opening the connection. This is defense in
depth, not a complete DNS-pinning implementation; registry review should reject
untrusted or user-controlled allowlist domains until the transport uses a client
that pins the validated address through connection establishment.
