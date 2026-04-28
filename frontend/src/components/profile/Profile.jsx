import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Settings, Calendar } from 'lucide-react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import PostCard from '../feed/PostCard';
import './Profile.css';

const Profile = () => {
  const { userId } = useParams();
  const { user: currentUser } = useAuth();
  const [profile, setProfile] = useState(null);
  const [posts, setPosts] = useState([]);
  const [counts, setCounts] = useState({ followers: 0, following: 0 });
  const [isFollowing, setIsFollowing] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchProfileData();
  }, [userId, currentUser]);

  const fetchProfileData = async () => {
    setLoading(true);
    try {
      // 1. User Info
      let userRes;
      if (userId) {
        userRes = await api.get(`/auth/users/${userId}`);
      } else {
        userRes = await api.get(`/auth/profile`);
      }
      console.log('Profile Info Response:', userRes.data);
      const profileData = userRes.data.data;
      setProfile(profileData);

      // 2. Counts
      const followerCountRes = await api.get(`/follows/count/followers/${profileData.userId}`);
      const followingCountRes = await api.get(`/follows/count/following/${profileData.userId}`);
      console.log('Follower Count Response:', followerCountRes.data);
      setCounts({
        followers: followerCountRes.data.data,
        following: followingCountRes.data.data
      });

      // 3. User Posts
      const postsRes = await api.get(`/posts/user/${profileData.userId}`);
      console.log('User Posts Response:', postsRes.data);
      setPosts(postsRes.data || []);

      // 4. Follow Status
      if (profileData.userId !== currentUser?.userId) {
        const followRes = await api.get(`/follows/isFollowing?followeeId=${profileData.userId}`);
        setIsFollowing(followRes.data.data);
      }
    } catch (err) {
      console.error('Error fetching profile:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleFollow = async () => {
    try {
      if (isFollowing) {
        await api.delete(`/follows?followeeId=${profile.userId}`);
        setCounts(prev => ({ ...prev, followers: prev.followers - 1 }));
      } else {
        await api.post('/follows', { followeeId: profile.userId });
        setCounts(prev => ({ ...prev, followers: prev.followers + 1 }));
      }
      setIsFollowing(!isFollowing);
      console.log('Follow action successful');
    } catch (err) {
      console.error('Follow error:', err);
    }
  };

  if (loading) return <div className="status">Loading profile...</div>;
  if (!profile) return <div className="status">User not found</div>;

  return (
    <div className="profile-page">
      <div className="profile-header card">
        <div className="banner"></div>
        <div className="profile-info-wrapper">
          <div className="profile-avatar">{profile.userId}</div>
          <div className="profile-actions">
            {profile.userId === currentUser?.userId ? (
              <button className="btn-secondary"><Settings size={18} /> Edit Profile</button>
            ) : (
              <button 
                className={isFollowing ? 'btn-secondary' : 'btn-primary'}
                onClick={handleFollow}
              >
                {isFollowing ? 'Following' : 'Follow'}
              </button>
            )}
          </div>
        </div>

        <div className="profile-details">
          <h2 className="display-name">{profile.fullName || profile.username}</h2>
          <span className="username">@{profile.username}</span>
          <p className="bio">{profile.bio || 'No bio yet.'}</p>
          <div className="profile-meta">
            <span><Calendar size={16} /> Joined {new Date(profile.createdAt).toLocaleDateString()}</span>
          </div>
          <div className="profile-stats">
            <span><strong>{counts.following}</strong> Following</span>
            <span><strong>{counts.followers}</strong> Followers</span>
          </div>
        </div>
      </div>

      <div className="profile-tabs">
        <button className="active">Posts</button>
        <button>Media</button>
        <button>Likes</button>
      </div>

      <div className="profile-posts">
        {posts.map(post => <PostCard key={post.postId} post={post} />)}
      </div>
    </div>
  );
};

export default Profile;
