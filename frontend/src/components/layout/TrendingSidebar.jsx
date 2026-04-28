import React, { useState, useEffect } from 'react';
import api from '../../services/api';
import './TrendingSidebar.css';

const TrendingSidebar = () => {
  const [hashtags, setHashtags] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchTrending = async () => {
      try {
        const res = await api.get('/search/hashtags/trending');
        setHashtags(res.data.data);
      } catch (err) {
        console.error('Error fetching trending hashtags:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchTrending();
  }, []);

  return (
    <div className="trending-sidebar card">
      <h3>Trending Hashtags</h3>
      {loading ? (
        <div className="status">Loading...</div>
      ) : hashtags.length > 0 ? (
        <div className="hashtag-list">
          {hashtags.map((tag, idx) => (
            <div key={idx} className="hashtag-item">
              <span className="tag-name">#{tag.tag || tag.tagName}</span>
              <span className="tag-count">{tag.postCount ?? tag.usageCount ?? 0} posts</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="status">No trending hashtags</div>
      )}
    </div>
  );
};

export default TrendingSidebar;
