import { useState } from 'react';
import { MD_SCREENS, type MdScreenKey } from './mdScreens';

export default function App() {
  const [activeKey, setActiveKey] = useState<MdScreenKey>(MD_SCREENS[0].key);
  const activeScreen = MD_SCREENS.find((screen) => screen.key === activeKey) ?? MD_SCREENS[0];
  const ActiveComponent = activeScreen.Component;

  return (
    <div className="app-shell">
      <nav className="app-nav" aria-label="Danh mục và quản trị">
        <div className="app-brand">PXVB</div>
        {MD_SCREENS.map((screen) => {
          const isCurrent = screen.key === activeKey;
          return (
            <button
              key={screen.key}
              type="button"
              className={isCurrent ? 'nav-item nav-item--current' : 'nav-item'}
              aria-current={isCurrent ? 'page' : undefined}
              onClick={() => setActiveKey(screen.key)}
            >
              {screen.label}
            </button>
          );
        })}
      </nav>
      <main className="app-main">
        <ActiveComponent />
      </main>
    </div>
  );
}
