import React, { useState, useRef } from 'react';
import { Image, Video, X, Send } from 'lucide-react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import './CreatePost.css';

const CreatePost = ({ onPostCreated }) => {
  const { user } = useAuth();
  const [content, setContent] = useState('');
  const [mediaFiles, setMediaFiles] = useState([]);
  const [previews, setPreviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const fileInputRef = useRef();

  const handleFileChange = (e) => {
    const files = Array.from(e.target.files);
    setMediaFiles([...mediaFiles, ...files]);
    const newPreviews = files.map(file => URL.createObjectURL(file));
    setPreviews([...previews, ...newPreviews]);
  };

  const removeMedia = (index) => {
    setMediaFiles(mediaFiles.filter((_, i) => i !== index));
    setPreviews(previews.filter((_, i) => i !== index));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!content.trim() && mediaFiles.length === 0) return;

    setLoading(true);
    try {
      const mediaUrls = [];
      
      // 1. Upload files
      for (const file of mediaFiles) {
        const formData = new FormData();
        formData.append('file', file);
        const res = await api.post('/media/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        console.log('Media Upload Response:', res.data);
        mediaUrls.push(res.data.data.url);
      }

      // 2. Create post
      const postData = {
        authorId: user.userId,
        content,
        mediaUrls,
        postType: mediaFiles.length > 0 ? 'MEDIA' : 'TEXT',
        visibility: 'PUBLIC'
      };
      
      const res = await api.post('/posts', postData);
      console.log('Create Post Response:', res.data);

      setContent('');
      setMediaFiles([]);
      setPreviews([]);
      if (onPostCreated) onPostCreated();
    } catch (err) {
      console.error('Error creating post:', err);
      alert('Failed to create post');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="create-post card">
      <form onSubmit={handleSubmit}>
        <textarea 
          placeholder="What's happening?" 
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={3}
        />

        {previews.length > 0 && (
          <div className="media-previews">
            {previews.map((src, idx) => (
              <div key={idx} className="preview-item">
                {mediaFiles[idx]?.type.startsWith('video') ? (
                  <video src={src} />
                ) : (
                  <img src={src} alt="Preview" />
                )}
                <button type="button" onClick={() => removeMedia(idx)} className="remove-btn">
                  <X size={16} />
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="create-post-actions">
          <div className="media-buttons">
            <button type="button" onClick={() => fileInputRef.current.click()}>
              <Image size={20} className="icon-blue" />
            </button>
            <button type="button" onClick={() => fileInputRef.current.click()}>
              <Video size={20} className="icon-purple" />
            </button>
            <input 
              type="file" 
              ref={fileInputRef} 
              hidden 
              multiple 
              accept="image/*,video/*"
              onChange={handleFileChange}
            />
          </div>
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Posting...' : <><Send size={18} /> Post</>}
          </button>
        </div>
      </form>
    </div>
  );
};

export default CreatePost;
