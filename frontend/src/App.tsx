import { NavLink, Route, Routes, Navigate } from "react-router-dom";
import Dashboard from "./pages/Dashboard";
import Calendar from "./pages/Calendar";
import LogSession from "./pages/LogSession";
import Analytics from "./pages/Analytics";
import SettingsPage from "./pages/SettingsPage";

export default function App() {
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">Sub <span>1:30</span></div>
        <div className="brand-sub">Half marathon tracker</div>
        <nav className="nav">
          <NavLink to="/" end>Home</NavLink>
          <NavLink to="/calendar">Calendar</NavLink>
          <NavLink to="/analytics">Analytics</NavLink>
          <NavLink to="/settings">Settings</NavLink>
        </nav>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/calendar" element={<Calendar />} />
          <Route path="/log/:date" element={<LogSession />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </main>
    </div>
  );
}
