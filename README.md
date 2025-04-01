#Basic instructions for setting up the database.
Run mysql along with some other goodies.
```
docker compose up
```
Login to mysql:
```
docker exec -it mysql /bin/mysql -h localhost -P 3306 -u root -p
```

# Create the database 
```
-- Create a new database
CREATE DATABASE questionare;

-- Create a new user with password
CREATE USER 'questionare'@'%' IDENTIFIED BY '123456';

-- Grant privileges to the user on the database
GRANT ALL PRIVILEGES ON questionare.* TO 'questionare'@'%';

-- Apply changes
FLUSH PRIVILEGES;
```

`



` docker run -d --name mysql-local \
  -e MYSQL_ROOT_PASSWORD=password \
  -e MYSQL_DATABASE=<your_database_name> \
  -e MYSQL_USER=<your_user_name> \
  -e MYSQL_PASSWORD=<your_user_password> \
  -p 3306:3306 \
  mysql/mysql-server:latest`