import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Vitest không bật globals, nên Testing Library không tự dọn DOM giữa các test.
afterEach(() => {
  cleanup();
});
