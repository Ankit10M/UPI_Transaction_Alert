const { PrismaClient } = require('@prisma/client');

class Database {
  constructor() {
    this.client = new PrismaClient({
      log: process.env.NODE_ENV === 'development' ? ['query', 'error', 'warn'] : ['error'],
    });
  }

  async connect() {
    try {
      await this.client.$connect();
      console.log('Connected to PostgreSQL database');
      return true;
    } catch (error) {
      console.error('Database connection error:', error);
      throw error;
    }
  }

  async disconnect() {
    try {
      await this.client.$disconnect();
      console.log('Disconnected from PostgreSQL database');
    } catch (error) {
      console.error('Database disconnection error:', error);
    }
  }

  get client() {
    return this._client;
  }

  set client(value) {
    this._client = value;
  }

  async query(sql, params = []) {
    return await this.client.$queryRaw(sql, params);
  }

  async transaction(callback) {
    return await this.client.$transaction(callback);
  }
}

module.exports = Database;