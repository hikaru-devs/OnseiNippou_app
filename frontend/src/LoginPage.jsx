import React, { useState } from 'react';

function LoginPage() {
  const [email, setEmail] = useState('');

  const handleGoogleLogin = () => {
    window.location.href = `${import.meta.env.VITE_API_URL}/oauth2/authorization/google`; // Redirect to Google OAuth
  };

  const handleEmailContinue = () => {
    alert(`You entered: ${email}`);
    // POST to backend if needed
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-white dark:bg-gray-900 dark:text-gray-200 px-4">
      <div className="w-full max-w-md space-y-6 text-center">
        <h1 className="text-4xl font-bold">Welcome to OnseiNippou</h1>
        <p className="text-gray-500 dark:text-gray-400">Sign in to your account</p>

        <div className="space-y-3">
          <button
            onClick={handleGoogleLogin}
            className="w-full flex items-center justify-center border border-gray-500 rounded-md py-2 px-4 bg-white hover:bg-gray-50 shadow-sm dark:bg-gray-800 dark:hover:bg-gray-700 dark:border-gray-600 text-gray-800 dark:text-gray-200"
          >
            <img
              src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg"
              alt="G"
              className="w-5 h-5 mr-2"
            />
            <span>Continue with Google</span>
          </button>

          <div className="flex items-center gap-2 text-gray-400 dark:text-gray-500">
            <hr className="flex-grow border-gray-300 dark:border-gray-700" />
            <span>or</span>
            <hr className="flex-grow border-gray-300 dark:border-gray-700" />
          </div>

          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            className="w-full px-4 py-2 border rounded-md border-gray-300 dark:bg-gray-800 dark:border-gray-600 dark:text-white disabled:opacity-50"
            disabled
          />

          <button
            onClick={handleEmailContinue}
            className="w-full py-2 px-4 rounded-md bg-blue-600 text-white font-semibold hover:bg-blue-700 transition disabled:opacity-50"
            disabled
          >
            Continue
          </button>

        </div>
      </div>
    </div>
  );
}

export default LoginPage;
