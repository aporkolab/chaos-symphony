# Stage 1: Build the Angular application
FROM node:20-alpine AS builder

# Set the working directory
WORKDIR /app

# Copy package configuration files
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci

# Copy the rest of the application source code
COPY . .

# Build the application for production
# The output will be in the /app/dist/chaos-symphony-dashboard directory
RUN npm run build -- --configuration=production

# ---

# Stage 2: Serve the application using Nginx
FROM nginx:1.25-alpine

# Remove the default Nginx configuration file
RUN rm /etc/nginx/conf.d/default.conf

# Copy the custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built application from the builder stage to Nginx's web root
COPY --from=builder /app/dist/chaos-symphony-dashboard/browser /usr/share/nginx/html

# Expose port 80 for the web server
EXPOSE 80
