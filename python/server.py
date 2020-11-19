from contextlib import suppress
from socket import socket, AF_INET, SOCK_DGRAM
from ast import literal_eval
from time import time, sleep
from threading import Thread
from random import choice


class Pong:
    def __init__(self):
        self.clients = {}
        self.expected_tokens = []
        self.game_coordinators = ["127.0.0.1"] # DDNS
        self.ball_position = [0.4921, 0.4861]
        self.ball_velocity = [0.0, 0.0]
        self.running_game = True
        self.phase = 'waiting'
        self.socket = socket(AF_INET, SOCK_DGRAM)
        self.socket.bind(("0.0.0.0", 5004))
        self.listen()

    def update_position(self):
        self.ball_velocity = [choice([-0.005, 0.005]), choice([-0.005, 0.005])]
        runs = 0
        start_time = time()
        while self.running_game:
            self.ball_position[0] += self.ball_velocity[0]
            self.ball_position[1] += self.ball_velocity[1]
            if not 0.0 < self.ball_position[1] < 1.0:
                self.ball_velocity[1] *= -1
            if not 0.0 < self.ball_position[0] < 1.0:
                if self.ball_position[0] < 0.0:
                    self.clients[self.client_by_id(1)]["score"] += 1
                elif self.ball_position[0] > 1.0:
                    self.clients[self.client_by_id(0)]["score"] += 1
                print("Score: %d vs %d" % (self.clients[self.client_by_id(0)]["score"], self.clients[self.client_by_id(1)]["score"]))
                self.ball_position = [0.4921, 0.4861]
                self.send({"data-type": "score", "0": self.clients[self.client_by_id(0)]["score"], "1": self.clients[self.client_by_id(1)]["score"]})
            for client in self.clients:
                position = self.clients[client]["position"]
                if self.overlapping(position[0], position[1], 0.015625, 0.13888888888, self.ball_position[0], self.ball_position[1], 0.015625, 0.0277777778):
                    relative_position = abs((position[1] - self.ball_position[1]) / 0.1388888)
                    self.ball_velocity[0] *= -1
                    updated_velocity = 0.0025
                    if relative_position <= 0.25:
                        updated_velocity = 0.01
                    elif relative_position <= 0.50:
                        updated_velocity = 0.0075
                    elif relative_position <= 0.75:
                        updated_velocity = 0.005
                    if self.ball_velocity[1] < 0:
                        self.ball_velocity[1] = -updated_velocity
                    else:
                        self.ball_velocity[1] = updated_velocity
            self.send({"data-type": "position", "x": self.ball_position[0], "y": self.ball_position[1]})
            runs += 1
            if (time() - start_time >= 1):
                print("runs: ", runs)
                start_time = time()
                runs = 0
            sleep(1 / 128)

    def start(self):
        Thread(target=self.update_position).start()

    def listen(self):
        while True:
            with suppress(Exception):
                received_data, address = self.socket.recvfrom(150)
                print(received_data)
                information = literal_eval(received_data.decode())
                if address not in self.clients:
                    if information["data-type"] == "auth":
                        if information["token"] in self.expected_tokens:
                            self.expected_tokens.remove(information["token"])
                            self.clients[address] = {"connected": int(time()), "identifier": len(self.clients), "score": 0}
                            self.clients[address]["position"] = [0.075, 0.45] if len(self.clients) == 1 else [0.9094, 0.45]
                            print("Client [%s:%d] joined the server." % (address[0], address[1]))
                            if len(self.clients) == 2:
                                print("Started match...")
                                self.phase = "ongoing"
                                self.start()
                    elif information["data-type"] == "prepare":
                        print(address)
                        if address[0] in self.game_coordinators: # TODO
                            self.phase = "preparing"
                            self.expected_tokens.extend([information["token_1"], information["token_2"]])
                            print("Game coordinator sent preparation request.")
                    elif information["data-type"] == "query":
                            self.socket.sendto(str.encode(str({"data-type": "response", "phase": self.phase})), address)
                    continue
                if information["data-type"] == "movement":
                    self.clients[address]["position"][1] += information["direction"] / 75
                    information["data-type"] = "placement"
                    information["identifier"] = self.clients[address]["identifier"]
                    self.send(information)

    def client_by_id(self, identifier):
        for client in self.clients:
            if int(self.clients[client]["identifier"]) == int(identifier):
                return client
        raise Exception("client with id %d not found", identifier)

    def overlapping(self, x1, y1, w1, h1, x2, y2, w2, h2):
        if not (x1 + w1 < x2 or x2 + w2 < x1 or y1 + h1 < y2 or y2 + h2 < y1):
            return True
        return

    def send(self, message):
        for client in self.clients:
            self.socket.sendto(str.encode(str(message)), client)


if __name__ == '__main__':
    Pong()
