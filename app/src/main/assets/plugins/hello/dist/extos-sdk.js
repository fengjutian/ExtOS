(function (global) {
  'use strict';

  const pending = new Map();
  const lifecycle = new EventTarget();
  let nextId = 1;

  global.__extosReceive = function (source) {
    const response = JSON.parse(source);
    const request = pending.get(response.id);
    if (!request) return;
    pending.delete(response.id);
    clearTimeout(request.timer);
    response.ok ? request.resolve(response.result) : request.reject(response.error);
  };

  function call(method, params, options) {
    const id = String(nextId++);
    const timeout = options && options.timeout || 15000;
    return new Promise(function (resolve, reject) {
      const timer = setTimeout(function () {
        pending.delete(id);
        reject({ code: 'TIMEOUT', message: method + ' timed out' });
      }, timeout);
      pending.set(id, { resolve: resolve, reject: reject, timer: timer });
      ExtOSNative.postMessage(JSON.stringify({ id: id, method: method, params: params || {} }));
    });
  }

  ['extosready', 'extosresume', 'extossuspend', 'extosshutdown'].forEach(function (name) {
    global.addEventListener(name, function () { lifecycle.dispatchEvent(new Event(name)); });
  });

  global.ext = Object.freeze({
    call: call,
    lifecycle: Object.freeze({
      on: function (event, listener) {
        lifecycle.addEventListener('extos' + event, listener);
        return function () { lifecycle.removeEventListener('extos' + event, listener); };
      }
    }),
    runtime: Object.freeze({ version: function () { return call('runtime.version'); } }),
    ui: Object.freeze({ toast: function (message) { return call('ui.toast', { message: message }); } }),
    storage: Object.freeze({
      get: function (key) { return call('storage.get', { key: key }); },
      set: function (key, value) { return call('storage.set', { key: key, value: value }); },
      remove: function (key) { return call('storage.remove', { key: key }); },
      clear: function () { return call('storage.clear'); }
    })
  });
})(window);
