import React, { useState, useEffect, useRef } from 'react';
import { Plus, X, Trash2, Image as ImageIcon, Type } from 'lucide-react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import './StoryBar.css';

const StoryBar = () => {
  const { user } = useAuth();
  const [stories, setStories] = useState([]);
  const [isUploading, setIsUploading] = useState(false);
  const [showOptions, setShowOptions] = useState(false);
  const [showTextPrompt, setShowTextPrompt] = useState(false);
  const [textStoryContent, setTextStoryContent] = useState('');
  const [viewStory, setViewStory] = useState(null);
  
  const fileInputRef = useRef(null);
  const DEFAULT_TEXT_BG = 'https://via.placeholder.com/1080x1920/1a1a2e/ffffff?text=';

  useEffect(() => {
    fetchStories();
  }, []);

  const fetchStories = async () => {
    try {
      const res = await api.get('/stories/active');
      setStories(res.data.data || []);
    } catch (err) {
      console.error('Error fetching stories:', err);
    }
  };

  const getMediaUrl = (url) => {
    if (url && url.startsWith('/api/v1/')) {
      return `http://localhost:9000${url}`;
    }
    return url;
  };

  const handleStoryUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setShowOptions(false);
    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const mediaRes = await api.post('/media/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      
      const uploadedMedia = mediaRes.data.data;
      
      const storyData = {
        mediaUrl: uploadedMedia.url,
        mediaType: uploadedMedia.mediaType || (file.type.startsWith('video') ? 'VIDEO' : 'IMAGE'),
        caption: ''
      };
      
      await api.post('/stories', storyData);
      fetchStories();
    } catch (err) {
      console.error('Error uploading story:', err);
      alert('Failed to upload story');
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const submitTextStory = async () => {
    if (!textStoryContent.trim()) return;

    setShowTextPrompt(false);
    setIsUploading(true);
    try {
      const storyData = {
        mediaUrl: DEFAULT_TEXT_BG + encodeURIComponent(textStoryContent.trim()),
        mediaType: 'IMAGE',
        caption: textStoryContent.trim()
      };
      
      await api.post('/stories', storyData);
      setTextStoryContent('');
      fetchStories();
    } catch (err) {
      console.error('Error uploading text story:', err);
      alert('Failed to upload text story');
    } finally {
      setIsUploading(false);
    }
  };

  const handleDeleteStory = async (storyId) => {
    try {
      await api.delete(`/stories/${storyId}`);
      setViewStory(null);
      fetchStories();
    } catch (err) {
      console.error('Error deleting story:', err);
      alert('Failed to delete story');
    }
  };

  const handleOpenStory = async (story) => {
    try {
      if (user && story.authorId !== user.userId) {
        await api.post(`/stories/${story.storyId}/view`);
      }
      setViewStory(story);
    } catch (err) {
      console.error('Error viewing story:', err);
      setViewStory(story);
    }
  };

  return (
    <div className="story-bar-container">
      <div className="story-bar">
        <div 
          className="story-item create-story" 
          onClick={() => !isUploading && setShowOptions(true)} 
          style={{ cursor: isUploading ? 'not-allowed' : 'pointer' }}
        >
          <div className="story-circle">
            {isUploading ? <span className="uploading-spinner">...</span> : <Plus size={24} />}
          </div>
          <span>{isUploading ? 'Uploading...' : 'Your Story'}</span>
        </div>
        
        <input 
          type="file" 
          ref={fileInputRef} 
          hidden 
          accept="image/*,video/*"
          onChange={handleStoryUpload}
        />

        {stories.map((story) => (
          <div key={story.storyId} className="story-item" onClick={() => handleOpenStory(story)}>
            <div className="story-circle active">
              {story.mediaUrl.includes('via.placeholder.com') ? (
                <div className="text-story-preview">
                   T
                </div>
              ) : (
                <img src={getMediaUrl(story.mediaUrl)} alt="Story" />
              )}
            </div>
            <span>User {story.authorId}</span>
          </div>
        ))}
      </div>

      {/* Options Modal */}
      {showOptions && (
        <div className="modal-overlay">
          <div className="options-modal">
            <button className="close-btn" onClick={() => setShowOptions(false)}><X size={20} /></button>
            <h3>Create Story</h3>
            <div className="options-grid">
              <button className="option-btn" onClick={() => { setShowOptions(false); fileInputRef.current?.click(); }}>
                <ImageIcon size={32} />
                <span>Photo/Video</span>
              </button>
              <button className="option-btn" onClick={() => { setShowOptions(false); setShowTextPrompt(true); }}>
                <Type size={32} />
                <span>Text</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Text Prompt Modal */}
      {showTextPrompt && (
        <div className="modal-overlay">
          <div className="text-prompt-modal">
            <button className="close-btn" onClick={() => setShowTextPrompt(false)}><X size={20} /></button>
            <h3>Write Text Story</h3>
            <textarea 
              value={textStoryContent} 
              onChange={(e) => setTextStoryContent(e.target.value)}
              placeholder="What's on your mind?"
              rows={4}
            />
            <button className="btn-primary" onClick={submitTextStory}>Post Story</button>
          </div>
        </div>
      )}

      {/* View Story Modal */}
      {viewStory && (
        <div className="modal-overlay story-view-overlay" onClick={() => setViewStory(null)}>
          <div className="story-view-modal" onClick={e => e.stopPropagation()}>
            <button className="close-btn" onClick={() => setViewStory(null)}><X size={24} color="white" /></button>
            
            {user && viewStory.authorId === user.userId && (
              <button className="delete-story-btn" onClick={() => handleDeleteStory(viewStory.storyId)}>
                <Trash2 size={20} /> Delete
              </button>
            )}

            <div className="story-view-content">
              {viewStory.mediaUrl.includes('via.placeholder.com') ? (
                <div className="story-text-display">
                  {viewStory.caption}
                </div>
              ) : viewStory.mediaType === 'VIDEO' ? (
                <video src={getMediaUrl(viewStory.mediaUrl)} controls autoPlay />
              ) : (
                <img src={getMediaUrl(viewStory.mediaUrl)} alt="Story View" />
              )}
            </div>
            <div className="story-info">
              <p>User {viewStory.authorId}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StoryBar;
