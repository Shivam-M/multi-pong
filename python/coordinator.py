from socket import socket, AF_INET, SOCK_STREAM, SOCK_DGRAM
from select import select
from ast import literal_eval
from threading import Thread
from time import sleep
from secrets import token_hex


class Pong:
    def __init__(self):
        self.socket = socket(AF_INET, SOCK_STREAM)
        self.socket.bind(("0.0.0.0", 4999))
        self.socket.listen(10)
        self.clients = [self.socket]
        self.cached_matches = {}
        self.searching_clients = []
        self.server_list = [
            ("127.0.0.1", 5000),
            ("127.0.0.1", 5001),
            ("127.0.0.1", 5002),
            ("127.0.0.1", 5003),
            ("127.0.0.1", 5004)
            ]
        Thread(target=self.check_status).start()
        self.listen()

    def _check_status(self):
        print("--")
        for server in self.server_list:
            try:
                query_socket = socket(AF_INET, SOCK_DGRAM)
                query_socket.connect(server)
                query_socket.settimeout(10)
                query_socket.send(str.encode(str({"data-type": "query"})))
                response, address = query_socket.recvfrom(150)
                information = literal_eval(response.decode())
                if information["data-type"] == "response":
                    self.cached_matches[server] = information
                    if information["phase"] == "waiting":
                        print(str(server) + " is available")
                    else:
                        print(str(server) + " is busy")
            except (TimeoutError, ConnectionResetError, Exception):
                print(str(server) + " is unresponsive")

    def check_status(self):
        while True:
            self._check_status()
            self.matchmake()
            sleep(10)

    def server_available(self):
        for server in self.server_list:
            try:
                query_socket = socket(AF_INET, SOCK_DGRAM)
                query_socket.connect(server)
                query_socket.settimeout(10)
                query_socket.send(str.encode(str({"data-type": "query"})))
                response, address = query_socket.recvfrom(150)
                information = literal_eval(response.decode())
                if information["data-type"] == "response":
                    if information["phase"] == "waiting":
                        return server
            except (TimeoutError, ConnectionResetError, Exception):
                print(str(server) + " did not respond to the query.")
        return False

    def prepare(self, client1, client2, server):
        print("prep")
        tokens = [token_hex(16), token_hex(16)]
        server_socket = socket(AF_INET, SOCK_DGRAM)
        server_socket.connect(server)
        server_socket.settimeout(10)
        server_socket.send(str.encode(str({"data-type": "prepare", "token_1": tokens[0], "token_2": tokens[1]})))
        #sleep(1)
        client1.send(str.encode(str({"data-type": "match", "address": server[0], "port": server[1], "token": tokens[0]}) + '\n'))
        client2.send(str.encode(str({"data-type": "match", "address": server[0], "port": server[1], "token": tokens[1]}) + '\n'))

    def matchmake(self):
        if len(self.searching_clients) >= 2:
            copied_searchers = self.searching_clients.copy()
            if server := self.server_available():
                self.prepare(copied_searchers[0], copied_searchers[1], server)
                self.searching_clients.remove(copied_searchers[0])
                self.searching_clients.remove(copied_searchers[1])
                print("Client [%s:%d] joined a match." % (
                copied_searchers[0].getpeername()[0], copied_searchers[0].getpeername()[1]))
                print("Client [%s:%d] joined a match." % (
                copied_searchers[1].getpeername()[0], copied_searchers[1].getpeername()[1]))

    def listen(self):
        while True:
            try:
                read_sockets, write_sockets, error_sockets = select(self.clients, [], [])
                for sock in read_sockets:
                    if sock == self.socket:
                        sockfd, address = self.socket.accept()
                        self.clients.append(sockfd)
                        print(f'Client [{address[0]}:{address[1]}] connected to the server.')
                    else:
                        try:
                            received_data = sock.recv(150).decode()
                        except:
                            print("Client [%s:%d] disconnected from the server." % (sock.getpeername()[0], sock.getpeername()[1]))
                            sock.close()
                            self.clients.remove(sock)
                            continue
                        if not received_data:
                            continue
                        information = literal_eval(received_data)
                        if information["data-type"] == "search":
                            self.searching_clients.append(sock)
                            self.matchmake()
            except Exception as error:
                print(error)




if __name__ == '__main__':
    Pong()
