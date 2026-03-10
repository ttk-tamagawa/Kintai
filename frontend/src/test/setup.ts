import "@testing-library/jest-dom/vitest";

// ========================================
// テストセットアップ
// jsdom で不足する Web API のポリフィルを定義する
// ========================================

// ResizeObserver: Radix UI コンポーネントが使用する
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;

// scrollIntoView: Radix Select 等がフォーカス管理に使用する
Element.prototype.scrollIntoView = () => {};

// matchMedia: レスポンシブ対応コンポーネントが使用する
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

// PointerEvent: Radix UI がポインターイベントを使用する
class PointerEventStub extends MouseEvent {
  readonly pointerId: number;
  readonly pointerType: string;
  constructor(type: string, params: PointerEventInit = {}) {
    super(type, params);
    this.pointerId = params.pointerId ?? 0;
    this.pointerType = params.pointerType ?? "";
  }
}
window.PointerEvent = PointerEventStub as unknown as typeof PointerEvent;

// hasPointerCapture / setPointerCapture / releasePointerCapture:
// Radix UI のプレス処理が呼び出す
Element.prototype.hasPointerCapture = () => false;
Element.prototype.setPointerCapture = () => {};
Element.prototype.releasePointerCapture = () => {};
