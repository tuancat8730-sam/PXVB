import { useState } from 'react';
import { MD_SCREENS } from './mdScreens';
import type { MdScreenKey } from './screenProps';

interface ActiveView {
  key: MdScreenKey;
  recordId: string | null;
}

const FIRST_VIEW: ActiveView = { key: MD_SCREENS[0].key, recordId: null };

export default function App() {
  const [view, setView] = useState<ActiveView>(FIRST_VIEW);
  const activeScreen = MD_SCREENS.find((screen) => screen.key === view.key) ?? MD_SCREENS[0];
  const ActiveComponent = activeScreen.Component;

  function navigate(key: MdScreenKey, recordId?: string) {
    setView({ key, recordId: recordId ?? null });
  }

  return (
    <div className="app-shell">
      <nav className="app-nav" aria-label="Danh mục và quản trị">
        <div className="app-brand">PXVB</div>
        {MD_SCREENS.map((screen) => {
          const isCurrent = screen.key === view.key;
          return (
            <button
              key={screen.key}
              type="button"
              className={isCurrent ? 'nav-item nav-item--current' : 'nav-item'}
              aria-current={isCurrent ? 'page' : undefined}
              onClick={() => navigate(screen.key)}
            >
              {screen.label}
            </button>
          );
        })}
      </nav>
      <main className="app-main">
        <ActiveComponent recordId={view.recordId} onNavigate={navigate} />
      </main>
    </div>
  );
}
