import React, { useState, useEffect } from 'react';
import { Bell, Heart, MessageSquare, UserPlus } from 'lucide-react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import './Notifications.css';

const extractData = (response) => response?.data?.data ?? response?.data ?? null;

const Notifications = () => {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const { user, setUnreadCount } = useAuth();

  useEffect(() => {
    if (!user) {
      setLoading(false);
      return;
    }
    fetchNotifications();
    fetchUnreadCount();

    const intervalId = window.setInterval(() => {
      fetchNotifications();
      fetchUnreadCount();
    }, 15000);

    return () => window.clearInterval(intervalId);
  }, [user?.userId]);

  const fetchNotifications = async () => {
    if (!user) return;
    try {
      const res = await api.get(`/notifications/${user.userId}`);
      const list = extractData(res);
      setNotifications(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error('Error fetching notifications:', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchUnreadCount = async () => {
    if (!user) return;
    try {
      const res = await api.get(`/notifications/unread-count/${user.userId}`);
      const count = Number(extractData(res));
      setUnreadCount(Number.isFinite(count) ? count : 0);
    } catch (err) {
      console.error('Error fetching unread count:', err);
    }
  };

  const markAllAsRead = async () => {
    try {
      await api.put('/notifications/read-all');
      await fetchNotifications();
      setUnreadCount(0);
    } catch (err) {
      console.error('Error marking notifications as read:', err);
    }
  };

  const getIcon = (type) => {
    switch (type) {
      case 'LIKE': return <Heart size={18} className="icon-red" />;
      case 'COMMENT': return <MessageSquare size={18} className="icon-blue" />;
      case 'FOLLOW': return <UserPlus size={18} className="icon-purple" />;
      default: return <Bell size={18} />;
    }
  };

  return (
    <div className="notifications-page">
      <div className="page-title-row">
        <h2 className="page-title">Notifications</h2>
        <button className="btn-secondary" onClick={markAllAsRead}>Mark all read</button>
      </div>
      
      <div className="notifications-list">
        {loading ? (
          <div className="status">Loading...</div>
        ) : notifications && notifications.length > 0 ? (
          notifications.map(notif => (
            <div key={notif.notificationId} className={`notif-item card ${!notif.isRead ? 'unread' : ''}`}>
              <div className="notif-icon">
                {getIcon(notif.type)}
              </div>
              <div className="notif-content">
                <p>{notif.message}</p>
                <span className="notif-time">{new Date(notif.createdAt).toLocaleDateString()}</span>
              </div>
              {!notif.isRead && <div className="unread-dot"></div>}
            </div>
          ))
        ) : (
          <div className="empty-state card">
            <Bell size={48} />
            <p>No notifications yet</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default Notifications;
