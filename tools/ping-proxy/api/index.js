export const config = {
  runtime: 'edge',
};

export default async function handler(request) {
  // CORS Preflight
  if (request.method === 'OPTIONS') {
    return new Response(null, {
      status: 204,
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
        'Access-Control-Allow-Headers': '*',
      },
    });
  }

  const url = new URL(request.url);

  // Vercel при rewrite /gemini/:path* -> /api/index.js автоматически добавляет query параметр ?path=...
  // Google Gemini API пытается забиндить все query-параметры на поля protobuf и выдает ошибку "Unknown name 'path'".
  // Поэтому вычищаем системный параметр 'path', оставляя все реальные параметры (например ?key=...)
  const searchParams = new URLSearchParams(url.search);
  searchParams.delete('path');
  const queryString = searchParams.toString() ? `?${searchParams.toString()}` : '';

  // 1. МАРШРУТИЗАЦИЯ НА GROQ API
  if (url.pathname.startsWith('/groq') || url.pathname.startsWith('/api/groq')) {
    const cleanPath = url.pathname.replace(/^\/(?:api\/)?groq/, '');
    const targetUrl = `https://api.groq.com${cleanPath}${queryString}`;

    const headers = new Headers();
    for (const [key, value] of request.headers.entries()) {
      const lower = key.toLowerCase();
      if (lower !== 'host' && !lower.startsWith('x-vercel-') && lower !== 'referer' && lower !== 'origin') {
        headers.set(key, value);
      }
    }

    const fetchOptions = {
      method: request.method,
      headers: headers,
      redirect: 'follow',
    };

    if (request.method !== 'GET' && request.method !== 'HEAD') {
      fetchOptions.body = await request.arrayBuffer();
    }

    try {
      const response = await fetch(targetUrl, fetchOptions);
      const resHeaders = new Headers(response.headers);
      resHeaders.set('Access-Control-Allow-Origin', '*');
      
      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: resHeaders,
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), {
        status: 502,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        },
      });
    }
  }

  // 2. МАРШРУТИЗАЦИЯ НА GOOGLE GEMINI API
  if (url.pathname.startsWith('/gemini') || url.pathname.startsWith('/api/gemini')) {
    const cleanPath = url.pathname.replace(/^\/(?:api\/)?gemini/, '');
    const targetUrl = `https://generativelanguage.googleapis.com${cleanPath}${queryString}`;

    const headers = new Headers();
    for (const [key, value] of request.headers.entries()) {
      const lower = key.toLowerCase();
      if (lower !== 'host' && !lower.startsWith('x-vercel-') && lower !== 'referer' && lower !== 'origin') {
        headers.set(key, value);
      }
    }

    // Если передан Bearer токен в Authorization, Gemini может требовать x-goog-api-key
    const authHeader = request.headers.get('authorization');
    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.replace('Bearer ', '').trim();
      headers.set('x-goog-api-key', token);
    }

    const fetchOptions = {
      method: request.method,
      headers: headers,
      redirect: 'follow',
    };

    if (request.method !== 'GET' && request.method !== 'HEAD') {
      fetchOptions.body = await request.arrayBuffer();
    }

    try {
      const response = await fetch(targetUrl, fetchOptions);
      const resHeaders = new Headers(response.headers);
      resHeaders.set('Access-Control-Allow-Origin', '*');

      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: resHeaders,
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), {
        status: 502,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        },
      });
    }
  }

  // 3. ХЕЛСЧЕК
  return new Response(JSON.stringify({
    status: "online",
    message: "Ping RP Vercel Reverse Proxy работает штатно.",
    endpoints: {
      groq: "/groq/openai/v1/chat/completions -> https://api.groq.com/openai/v1/chat/completions",
      gemini: "/gemini/v1beta/models/... -> https://generativelanguage.googleapis.com/v1beta/models/..."
    }
  }), {
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*"
    }
  });
}
