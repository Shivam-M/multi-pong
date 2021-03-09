
namespace pong_network {
    class Network {
        public static void Main(string[] args) {
            if (args.Length == 0) {
                new Coordinator();
            } else if (args[0].Equals("-server")) {
                new Server(args.Length == 2 ? int.Parse(args[1]) : Server.PORT);
            }
        }
    }
}
