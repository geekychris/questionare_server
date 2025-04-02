# Questionnaire Application

A full-stack application for creating and managing questionnaire campaigns. Users can create campaigns, add questions, and collect responses through a user-friendly interface.

## Features

- Create and manage questionnaire campaigns
- Support for multiple-choice and text questions
- User-friendly survey interface
- Real-time feedback notifications
- Secure API endpoints
- Responsive Material-UI design

## Technology Stack

### Backend
- Java 17
- Spring Boot 3.x
- Spring Data JPA
- Spring Security
- H2 Database (can be configured for other databases)
- Maven for dependency management

### Frontend
- React 18
- Material-UI v7
- React Router v7
- Axios for API communication
- Context API for state management

## Prerequisites

- Java Development Kit (JDK) 17 or later
- Node.js 16 or later
- npm or yarn package manager
- Docker and Docker Compose (for containerized deployment)

## Project Structure

```
questionnaire-app/
├── backend/                 # Spring Boot application
│   ├── src/
│   └── pom.xml
└── frontend/               # React application
    ├── src/
    ├── public/
    └── package.json
```

## Local Development Setup

### Backend Setup

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Build the application:
   ```bash
   ./mvnw clean install
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

The backend will start on http://localhost:8080

### Frontend Setup

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the development server:
   ```bash
   npm start
   ```

The frontend will start on http://localhost:3000

## Docker Deployment

The application can be run using Docker containers. To build and run the application:

1. Build the containers:
   ```bash
   docker build -t questionnaire-service .
   ```

2. Start the services:
   ```bash
   docker-compose up -d
   ```
   ```bash
    docker run -p 8080:8080 questionnaire-service
    ```

The application will be available at http://localhost:3000

To stop the services:
```bash
docker-compose down
```

## Docker Build Instructions

The application can be built and run using Docker. The Dockerfile uses a multi-stage build process to create an optimized container image.

### Building the Docker Image

To build the Docker image:
```bash
docker build -t questionnaire-service .
```

This will create a Docker image named `questionnaire-service` using a multi-stage build process to minimize the final image size:
1. First stage uses maven:3.8-openjdk-17 to build the application
2. Second stage uses openjdk:17-jdk-slim for a smaller runtime image

### Running with Docker

To run the application in a Docker container:
```bash
docker run -p 8080:8080 questionnaire-service
```

The application will be accessible at http://localhost:8080

### Environment Variables

You can configure the application using environment variables when running the container:
```bash
docker run -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/questionnaire \
    -e SPRING_DATASOURCE_USERNAME=dbuser \
    -e SPRING_DATASOURCE_PASSWORD=dbpass \
    questionnaire-service
```

### Docker Compose

The application can also be run using Docker Compose, which will set up both the application and its dependencies:
```bash
docker-compose up
```

This will start both the application and the required MySQL database.

## API Documentation

The backend provides the following main endpoints:

### Campaigns
- GET /api/campaigns - List all campaigns
- POST /api/campaigns - Create a new campaign
- GET /api/campaigns/{id} - Get campaign details
- PUT /api/campaigns/{id} - Update a campaign
- DELETE /api/campaigns/{id} - Delete a campaign

### Questions
- GET /api/campaigns/{id}/questions - List questions for a campaign
- POST /api/campaigns/{id}/questions - Add a question
- PUT /api/campaigns/{id}/questions/{questionId} - Update a question
- DELETE /api/campaigns/{id}/questions/{questionId} - Delete a question

### Responses
- POST /api/campaigns/{id}/responses/questions/{questionId} - Submit a response
- GET /api/campaigns/{id}/responses/users/{userId} - Get user's responses

## Security

The application implements basic authentication for admin endpoints. In production, configure appropriate security measures and update the CORS settings in `WebConfig.java`.

## Building for Production

### Backend
```bash
cd backend
./mvnw clean package -DskipTests
```

### Frontend
```bash
cd frontend
npm run build
```

The frontend build will be created in the `frontend/build` directory, ready for deployment.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.






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
