# HackerX Translate Frontend

This is the frontend application for HackerX Translate, built with React.

## Prerequisites

- Docker installed on your system.

## Project Structure


## Building and Running the Docker Image

1. **Navigate to your project's root directory in your terminal**:

    ```sh
    cd hackerx-translate-frontend
    ```

2. **Build the Docker image**:

    ```sh
    docker build -t hackerx-translate-frontend .
    ```

3. **Run the Docker container**:

    ```sh
    docker run -p 80:80 hackerx-translate-frontend
    ```

    This will start your React application in a Docker container, accessible at `http://localhost`.

4. **Ensure the backend is running on `http://localhost:8080`**:

    Make sure your backend server is running on `http://localhost:8080` or adjust the proxy settings in the `nginx.conf` file if needed.

## Nginx Configuration

Here is the Nginx configuration used to serve the React application and proxy API requests:

```nginx
server {
    listen 80;
    
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        try_files $uri /index.html;
    }

    # Proxy API requests to the backend
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}

5. **Environment Variables and API Keys**:
   - If your project requires any environment variables or API keys, provide instructions on how to set them up. If these are stored in a separate file (like `.env`), include a template (e.g., `.env.example`) and instructions to create their own file with the actual values.

6. **Zip the Project**:
   - Compress the entire project directory (excluding the `node_modules` directory to reduce size) into a zip file for easier sharing.

7. **Send the Zip File**:
   - Share the zip file with the person responsible for hosting it. You can use email, a file-sharing service, or a version control system like GitHub or GitLab to share the files.

Here's how to exclude the `node_modules` directory and create a zip file:

```sh
zip -r hackerx-translate-frontend.zip hackerx-translate-frontend -x "*/node_modules/*"
