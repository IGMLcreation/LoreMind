FROM node:20-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .

# Neutralise les URLs absolues hardcodees dans les services (dette assumee :
# une refacto propre passerait par src/environments/*.ts + fileReplacements).
# Le reverse proxy nginx route /api/ vers core:8080, donc chemin relatif OK.
RUN find src -type f -name "*.ts" -exec sed -i "s|http://localhost:8080||g" {} +

RUN npm run build -- --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/web /usr/share/nginx/html
EXPOSE 80
