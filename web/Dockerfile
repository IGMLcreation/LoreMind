FROM node:20-bookworm-slim AS build
WORKDIR /build
# PAS de mise à jour de npm ici : « npm@latest » est une cible mouvante qui finit
# par exiger un Node plus récent que l'image (npm 12 -> Node >= 22) et casse le
# build. Le npm livré avec l'image suffit (npm ci supporte lockfileVersion 3).
COPY package*.json ./
RUN npm ci --include=dev --ignore-scripts --no-audit --no-fund --no-progress
COPY . .

RUN npm run build -- --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/web /usr/share/nginx/html
EXPOSE 80
