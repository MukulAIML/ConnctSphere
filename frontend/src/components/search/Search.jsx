import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Search as SearchIcon, User as UserIcon, FileText } from 'lucide-react';
import api from '../../services/api';
import PostCard from '../feed/PostCard';
import './Search.css';

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

const Search = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState(searchParams.get('q') || '');
  const [type, setType] = useState(searchParams.get('type') === 'users' ? 'users' : 'posts');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  useEffect(() => {
    const queryParam = searchParams.get('q') || '';
    const typeParam = searchParams.get('type') === 'users' ? 'users' : 'posts';

    setQuery(queryParam);
    setType(typeParam);

    if (queryParam.trim()) {
      runSearch(queryParam, typeParam);
    } else {
      setHasSearched(false);
      setResults([]);
    }
  }, [searchParams]);

  const runSearch = async (searchQuery, selectedType) => {
    if (!searchQuery.trim()) return;

    setLoading(true);
    setHasSearched(true);
    try {
      let res;
      if (selectedType === 'posts') {
        res = await api.get(`/posts/search?keyword=${encodeURIComponent(searchQuery)}`);
      } else {
        res = await api.get(`/auth/search?q=${encodeURIComponent(searchQuery)}`);
      }
      setResults(extractList(res));
    } catch (err) {
      console.error('Search error:', err);
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (e) => {
    e.preventDefault();
    const trimmedQuery = query.trim();
    if (!trimmedQuery) return;
    setSearchParams({ q: trimmedQuery, type });
  };

  const handleTypeChange = (nextType) => {
    setType(nextType);
    setResults([]);

    const trimmedQuery = query.trim();
    if (trimmedQuery) {
      setSearchParams({ q: trimmedQuery, type: nextType });
      return;
    }

    setHasSearched(false);
    setSearchParams({ type: nextType });
  };

  return (
    <div className="search-page">
      <form className="search-bar card" onSubmit={handleSearch}>
        <SearchIcon size={20} />
        <input 
          type="text" 
          placeholder={`Search ${type}...`} 
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button type="submit" className="btn-primary">Search</button>
      </form>

      <div className="search-filters">
        <button 
          type="button"
          className={type === 'posts' ? 'active' : ''} 
          onClick={() => handleTypeChange('posts')}
        >
          <FileText size={18} /> Posts
        </button>
        <button 
          type="button"
          className={type === 'users' ? 'active' : ''} 
          onClick={() => handleTypeChange('users')}
        >
          <UserIcon size={18} /> Users
        </button>
      </div>

      <div className="search-results">
        {loading ? (
          <div className="status">Searching...</div>
        ) : results && results.length > 0 ? (
          type === 'posts' ? (
            results.map(post => <PostCard key={post.postId} post={post} />)
          ) : (
            results.map(foundUser => (
              <div key={foundUser.userId} className="user-result card">
                <div className="avatar">{foundUser.userId}</div>
                <div className="user-info">
                  <h4>{foundUser.fullName || foundUser.username}</h4>
                  <span>@{foundUser.username}</span>
                </div>
                <button
                  className="btn-secondary"
                  onClick={() => navigate(`/profile/${foundUser.userId}`)}
                >
                  View Profile
                </button>
              </div>
            ))
          )
        ) : hasSearched && !loading && (
          <div className="status">No results found for "{query}"</div>
        )}
      </div>
    </div>
  );
};

export default Search;
