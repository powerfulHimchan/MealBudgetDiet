let nativePort: MessagePort | undefined;

export function setWidgetPort(port?: MessagePort) {
  nativePort?.close();
  nativePort = port;
  nativePort?.postMessage("sikbi:refresh");
}

export function notifyWidget(url: string) {
  const reset = /\/auth\/(logout|login|register|register-ledger|password-resets)$/.test(url)
    || /\/account\/(withdraw|password)$/.test(url);
  nativePort?.postMessage(reset ? "sikbi:logout" : "sikbi:refresh");
}
