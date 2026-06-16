FROM node:20-bookworm-slim AS build
WORKDIR /build
RUN npm install -g npm@latest
COPY package*.json ./
RUN npm ci --include=dev --ignore-scripts --no-audit --no-fund --no-progress
COPY . .

RUN npm run build -- --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/web /usr/share/nginx/html
EXPOSE 80
