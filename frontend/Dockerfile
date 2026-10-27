# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

# ---- serve ----
FROM nginx:1.27-alpine
# *.template files are run through envsubst at container start (PORT, BACKEND_URL, BACKEND_HOST)
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
ENV PORT=8080 BACKEND_URL=http://backend:8080 BACKEND_HOST=backend
EXPOSE 8080
