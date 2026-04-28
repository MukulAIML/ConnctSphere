import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Heart, MessageCircle, Share2, MoreHorizontal, Send } from 'lucide-react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import './PostCard.css';

const userCache = new Map();

const extractData = (response) => response?.data?.data ?? response?.data ?? null;

const getSafeCount = (value) => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
};

const PostCard = ({ post }) => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [liked, setLiked] = useState(false);
  const [likesCount, setLikesCount] = useState(getSafeCount(post.likesCount));
  const [likeLoading, setLikeLoading] = useState(false);
  const [showComments, setShowComments] = useState(false);
  const [comments, setComments] = useState([]);
  const [newComment, setNewComment] = useState('');
  const [commentsCount, setCommentsCount] = useState(getSafeCount(post.commentsCount));
  const [authorProfile, setAuthorProfile] = useState(null);
  const [commentProfiles, setCommentProfiles] = useState({});

  useEffect(() => {
    setLikesCount(getSafeCount(post.likesCount));
    setCommentsCount(getSafeCount(post.commentsCount));
  }, [post.postId, post.likesCount, post.commentsCount]);

  useEffect(() => {
    loadLikeState();
  }, [post.postId, user?.userId]);

  useEffect(() => {
    loadAuthorProfile(post.authorId);
  }, [post.authorId]);

  const loadAuthorProfile = async (authorId) => {
    if (!authorId) return;
    if (userCache.has(authorId)) {
      setAuthorProfile(userCache.get(authorId));
      return;
    }
    try {
      const res = await api.get(`/auth/users/${authorId}`);
      const profile = extractData(res) || null;
      if (profile) {
        userCache.set(authorId, profile);
      }
      setAuthorProfile(profile);
    } catch (err) {
      setAuthorProfile(null);
      console.error('Error fetching author profile:', err);
    }
  };

  const hydrateCommentProfiles = async (commentItems) => {
    if (!commentItems || commentItems.length === 0) return;

    const uniqueAuthorIds = [...new Set(commentItems.map((item) => item.authorId).filter(Boolean))];
    const missingAuthorIds = uniqueAuthorIds.filter((authorId) => !userCache.has(authorId));

    try {
      await Promise.all(
        missingAuthorIds.map(async (authorId) => {
          const res = await api.get(`/auth/users/${authorId}`);
          const profile = extractData(res) || null;
          if (profile) {
            userCache.set(authorId, profile);
          }
        })
      );
    } catch (err) {
      console.error('Error hydrating comment authors:', err);
    }

    const mappedProfiles = {};
    uniqueAuthorIds.forEach((authorId) => {
      if (userCache.has(authorId)) {
        mappedProfiles[authorId] = userCache.get(authorId);
      }
    });
    setCommentProfiles(mappedProfiles);
  };

  const loadLikeState = async () => {
    if (!user || !post.postId) {
      setLiked(false);
      return;
    }
    try {
      const [statusRes, countRes] = await Promise.all([
        api.get(`/likes/hasLiked?targetId=${post.postId}&targetType=POST`),
        api.get(`/likes/count?targetId=${post.postId}&targetType=POST`)
      ]);
      setLiked(Boolean(extractData(statusRes)));
      setLikesCount(getSafeCount(extractData(countRes)));
    } catch (err) {
      console.error('Error loading like state:', err);
    }
  };

  const fetchComments = async () => {
    try {
      const res = await api.get(`/comments?postId=${post.postId}`);
      const fetchedComments = extractData(res) || [];
      setComments(fetchedComments);
      setCommentsCount(fetchedComments.length);
      hydrateCommentProfiles(fetchedComments);
    } catch (err) {
      console.error('Error fetching comments:', err);
    }
  };

  const handleToggleLike = async () => {
    if (!user || likeLoading) return;

    setLikeLoading(true);
    const previousLiked = liked;
    const previousCount = likesCount;
    const nextLiked = !liked;

    setLiked(nextLiked);
    setLikesCount((prev) => Math.max(0, prev + (nextLiked ? 1 : -1)));

    try {
      if (previousLiked) {
        await api.delete(`/likes?targetId=${post.postId}&targetType=POST`);
      } else {
        await api.post('/likes', {
          targetId: post.postId,
          targetType: 'POST',
          reactionType: 'LIKE'
        });
      }

      await loadLikeState();
    } catch (err) {
      console.error('Like error:', err);
      setLiked(previousLiked);
      setLikesCount(previousCount);
    } finally {
      setLikeLoading(false);
    }
  };

  const handleAddComment = async (e) => {
    e.preventDefault();
    if (!newComment.trim()) return;

    try {
      const res = await api.post('/comments', {
        postId: post.postId,
        content: newComment
      });
      setNewComment('');
      fetchComments(); // Refresh comments list
    } catch (err) {
      console.error('Error adding comment:', err);
    }
  };

  const toggleComments = () => {
    if (!showComments) {
      fetchComments();
    }
    setShowComments(!showComments);
  };

  const renderContent = (content) => {
    if (!content) return null;
    return content.split(/(\s+)/).map((word, i) => {
      if (word.startsWith('#')) {
        return (
          <span 
            key={i} 
            className="hashtag" 
            onClick={() => navigate(`/search?q=${word.substring(1)}`)}
          >
            {word}
          </span>
        );
      }
      return word;
    });
  };

  const getMediaUrl = (url) => {
    if (url && url.startsWith('/api/v1/')) {
      return `http://localhost:9000${url}`;
    }
    return url;
  };

  const getUserLabel = (userId, fallback = null) => {
    const profile = fallback || commentProfiles[userId] || userCache.get(userId);
    return profile?.fullName || profile?.username || `User ${userId}`;
  };

  const authorLabel = getUserLabel(post.authorId, authorProfile);

  return (
    <div className="post-card card">
      <div className="post-header">
        <div className="user-info" onClick={() => navigate(`/profile/${post.authorId}`)}>
          <div className="avatar">{post.authorId}</div>
          <div>
            <h4 className="username">{authorLabel}</h4>
            <span className="timestamp">{new Date(post.createdAt).toLocaleDateString()}</span>
          </div>
        </div>
        <button><MoreHorizontal size={20} /></button>
      </div>

      <div className="post-content">
        <p>{renderContent(post.content)}</p>
      </div>

      {post.mediaUrls && post.mediaUrls.length > 0 && (
        <div className="post-media">
          {post.mediaUrls.map((url, idx) => (
            <div key={idx} className="media-item">
              {url && (url.toLowerCase().endsWith('.mp4') || url.toLowerCase().endsWith('.webm') || url.toLowerCase().endsWith('.ogg')) ? (
                <video src={getMediaUrl(url)} controls />
              ) : (
                <img src={getMediaUrl(url)} alt="Post media" />
              )}
            </div>
          ))}
        </div>
      )}

      <div className="post-actions">
        <button
          onClick={handleToggleLike}
          className={liked ? 'action-btn liked' : 'action-btn'}
          disabled={likeLoading}
        >
          <Heart size={20} fill={liked ? 'currentColor' : 'none'} />
          <span>{likesCount}</span>
        </button>
        <button className="action-btn" onClick={toggleComments}>
          <MessageCircle size={20} />
          <span>{commentsCount}</span>
        </button>
        <button className="action-btn">
          <Share2 size={20} />
        </button>
      </div>

      {showComments && (
        <div className="comments-section">
          <form className="comment-input" onSubmit={handleAddComment}>
            <input 
              type="text" 
              placeholder="Write a comment..." 
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
            />
            <button type="submit"><Send size={18} /></button>
          </form>
          
          <div className="comments-list">
            {comments.map(c => (
              <div key={c.commentId} className="comment-item">
                <div className="comment-avatar">{c.authorId}</div>
                <div className="comment-body">
                  <strong>{getUserLabel(c.authorId)}</strong>
                  <p>{c.content}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default PostCard;
