from tkinter import *
from os import _exit
from threading import Thread
from ast import literal_eval
from socket import socket, AF_INET, SOCK_DGRAM, SOCK_STREAM


class Pong:
    def __init__(self):
        self.window = Tk()
        self.window.geometry(f"1280x720+{self.window.winfo_screenwidth() // 2 - 1280 // 2}+50")
        self.window.configure(bg='#282828')
        self.window.title('Pong - searching for a match... - press F to toggle fullscreen')

        self.address = None
        self.coordinator = socket(AF_INET, SOCK_STREAM)
        self.socket = socket(AF_INET, SOCK_DGRAM)
        self.connect()

        Thread(target=self.listen_coordinator).start()

        self.paddles = [Frame(self.window, bg="#FFFFFF"), Frame(self.window, width=20, height=100, bg="#FFFFFF")]
        self.paddles[0].place(relx=.075, rely=.45, relwidth=20/1280, relheight=100/720)
        self.paddles[1].place(relx=.925-0.0156, rely=.45, relwidth=20/1280, relheight=100/720)

        self.ball = Frame(self.window, width=20, height=20, bg="#FFFFFF")
        self.ball.place(relx=0.4921, rely=.4861, relwidth=20/1280, relheight=20/720)

        self.send({"data-type": "search"})

        self.window.bind("<Up>", lambda event: self.send_move(-1))
        self.window.bind("<Down>", lambda event: self.send_move(1))
        self.window.bind("<F>", lambda event: self.window.attributes("-fullscreen", True if not self.window.wm_attributes("-fullscreen") else False))
        self.window.bind("<f>", lambda event: self.window.attributes("-fullscreen", True if not self.window.wm_attributes("-fullscreen") else False))
        self.window.mainloop()

    def receive_move(self, identifier, direction):
        paddle = self.paddles[identifier]
        paddle.place(relx=paddle.winfo_x() / 1280, rely=(paddle.winfo_y() / 720) + (direction / 75))
    
    def send_move(self, direction):
        self.game_send({"data-type": "movement", "direction": direction})

    def game_send(self, message):
        if self.address:
            self.socket.sendto(str.encode(str(message) + "\n"), self.address)

    def send(self, message):
        self.coordinator.send(str.encode(str(message) + "\n"))

    def listen(self):
        while True:
            received_data, address = self.socket.recvfrom(150)
            if "position" not in received_data.decode():
                print(received_data)
            if received_data:
                try:
                    information = literal_eval(received_data.decode())
                    if information["data-type"] == "placement":
                        assert int(information["identifier"]) in [0, 1] # in case someone sends bad data to cheat???
                        self.receive_move(information["identifier"], int(information["direction"]))
                    elif information["data-type"] == "position":
                        self.ball.place(relx=information["x"], rely=information["y"])
                    elif information["data-type"] == "score":
                        self.window.title(f"Pong - {information['0']} vs {information['1']}")
                except Exception as e:
                    print(e)

    def listen_coordinator(self):
        while True:
            try:
                received_data = self.coordinator.recv(150).decode()
                print(received_data)
                information = literal_eval(received_data)
                if information["data-type"] == "match":
                    self.address = (information["address"], int(information["port"]))
                    self.game_send({"data-type": "auth", "token": information["token"]})
                    self.window.title("Pong - playing...")
                    Thread(target=self.listen).start()
            except Exception as error:
                print("Lost connection to game coordinator."); break

    def connect(self):
        try:
            self.coordinator.connect(("127.0.0.1", 4999))
        except ConnectionRefusedError:
            print("Failed to connect to the server.")
            _exit(-1)


if __name__ == '__main__':
    Pong()
