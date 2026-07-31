# --------------------------------------------------
# Stage 1: Build the React application
# --------------------------------------------------
#FROM node:22.1.0-alpine AS build
FROM node:20-alpine AS build


# Add maintainer label
LABEL maintainer="rajkumar.nilkant@digitialsherpa.ai"

# Set working directory
WORKDIR /app

# Add /app/node_modules/.bin to PATH
# This allows npm scripts to find binaries like react-scripts, vite, etc.
ENV PATH=/app/node_modules/.bin:$PATH

# Copy dependency files first for better build caching
COPY package.json package-lock.json ./

# Install dependencies (clean, reproducible install)
RUN npm ci --legacy-peer-deps

# Copy application source
COPY . .

# Build the React app
RUN npm run build

# --------------------------------------------------
# Stage 2: Serve the React app with Nginx
# --------------------------------------------------
FROM nginx:1.27-alpine

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy build output from the previous stage
COPY --from=build /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80 for web traffic
EXPOSE 6027

# Health check (optional but recommended)
HEALTHCHECK --interval=30s --timeout=5s \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:6027/ || exit 1

# Start Nginx in the foreground
CMD ["nginx", "-g", "daemon off;"]