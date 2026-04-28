import React, { useState, useEffect } from 'react';
import StoryBar from '../stories/StoryBar';
import CreatePost from './CreatePost';
import PostCard from './PostCard';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';

const extractList = (response) => {
  const payload = response?.data;
  if (Array.isArray(payload)) {
    return payload;
  }
  if (Array.isArray(payload?.data)) {
    return payload.data;
  }
  return [];
};

const HomeFeed = () => {
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const { user } = useAuth();

  useEffect(() => {
    if (user) {
      fetchFeed();
    }
  }, [user]);

  const fetchFeed = async () => {
    setLoading(true);
    try {
      const res = await api.get(`/posts/feed/${user.userId}`);
      setPosts(extractList(res));
    } catch (err) {
      console.error('Error fetching feed:', err);
      setPosts([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="home-feed">
      <StoryBar />
      <CreatePost onPostCreated={fetchFeed} />
      
      <div className="posts-container">
        {loading ? (
          <div className="status">Loading your feed...</div>
        ) : posts && posts.length > 0 ? (
          posts.map(post => (
            <PostCard key={post.postId} post={post} />
          ))
        ) : (
          <div className="empty-state card">
            <h3>No posts yet</h3>
            <p>Follow some users to see their posts here!</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default HomeFeed;
