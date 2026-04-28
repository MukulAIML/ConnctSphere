import React, { useEffect } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { 
  Home, 
  Search as SearchIcon, 
  Bell, 
  User, 
  LogOut, 
  PlusSquare, 
  Compass,
  Play
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import TrendingSidebar from './TrendingSidebar';
import './MainLayout.css';

const MainLayout = () => {
  const { logout, user, unreadCount, setUnreadCount } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!user) return;

    const fetchUnreadCount = async () => {
      try {
        const res = await api.get(`/notifications/unread-count/${user.userId}`);
        const rawCount = res?.data?.data ?? res?.data ?? 0;
        const parsedCount = Number(rawCount);
        setUnreadCount(Number.isFinite(parsedCount) ? parsedCount : 0);
      } catch (err) {
        console.error('Error refreshing unread count:', err);
      }
    };

    fetchUnreadCount();
    const intervalId = window.setInterval(fetchUnreadCount, 15000);
    return () => window.clearInterval(intervalId);
  }, [user?.userId, setUnreadCount]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="main-layout">
      {/* Sidebar */}
      <aside className="sidebar glass">
        <div className="logo">
          <h1 className="gradient-text">ConnectSphere</h1>
        </div>

        <nav className="nav-links">
          <NavLink to="/feed" className={({isActive}) => isActive ? 'nav-item active' : 'nav-item'}>
            <Home size={24} />
            <span>Home</span>
          </NavLink>
          
          <NavLink to="/search" className={({isActive}) => isActive ? 'nav-item active' : 'nav-item'}>
            <SearchIcon size={24} />
            <span>Search</span>
          </NavLink>
          
          <NavLink to="/notifications" className={({isActive}) => isActive ? 'nav-item active' : 'nav-item'}>
            <div className="icon-wrapper">
              <Bell size={24} />
              {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
            </div>
            <span>Notifications</span>
          </NavLink>
          
          <NavLink to="/profile" className={({isActive}) => isActive ? 'nav-item active' : 'nav-item'} end>
            <User size={24} />
            <span>Profile</span>
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          <button onClick={handleLogout} className="nav-item logout">
            <LogOut size={24} />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="content-area">
        <div className="container">
          <Outlet />
        </div>
      </main>

      {/* Right Sidebar (Trending/Suggestions) */}
      <aside className="right-sidebar">
        <TrendingSidebar />
      </aside>
    </div>
  );
};

export default MainLayout;
