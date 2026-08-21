# Capability bridge protocol, draft 0

Plugins see one native transport function:

```js
ExtOSNative.postMessage(JSON.stringify(request))
```

Application code should use the future `@extos/sdk` wrapper rather than calling
this transport directly.

## Request

```json
{
  "id": "request-1",
  "method": "ui.toast",
  "params": { "message": "Hello" }
}
```

IDs contain 1–64 ASCII letters, digits, underscores, or hyphens. Requests are
limited to 16 KiB of UTF-8. Each method validates its own parameter object and
size limits.

## Response

The host invokes `window.__extosReceive(serializedResponse)`. The argument is a
JSON string, not executable plugin input.

```json
{
  "id": "request-1",
  "ok": true,
  "result": null
}
```

Errors are stable machine-readable codes with human-readable messages:

```json
{
  "id": "request-1",
  "ok": false,
  "error": {
    "code": "CAPABILITY_DENIED",
    "message": "User did not grant ui.toast"
  }
}
```

Initial codes are `INVALID_REQUEST`, `METHOD_NOT_FOUND`, `CAPABILITY_DENIED`,
and `INTERNAL_ERROR`. Internal exception details are never returned to plugins.

## Authorization

The method name is also its capability identifier in draft 0. Dispatch occurs
only after the policy engine confirms both manifest declaration and user grant.
Transport availability does not imply authorization.

## Lifecycle

The host dispatches an `extosready` event after the plugin document finishes
loading. Future versions will add suspend, resume, and shutdown events and will
define cancellation and timeout behavior for pending requests.
